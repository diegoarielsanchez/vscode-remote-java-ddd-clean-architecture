import "server-only";
import { unstable_cache } from "next/cache";
import { z } from "zod";
import { CATALOG_TAG, CatalogProductSchema, SPECIALTIES_TAG, SpecialtySchema } from "./cacheable.ts";
import { env } from "./env.ts";
import { callGateway, seg, type GatewayRequest } from "./gateway.ts";
import { GatewayError, emptyOnNotFound, isNotFound } from "./gateway-error.ts";
import { expiryOf, nowSeconds } from "./token.ts";

/** Safety net when no one calls /bff/revalidate (keep equal to `revalidate` in app/(catalog)). */
const REVALIDATE_SECONDS = 3600;

let account: { token: string; expiresAt: number } | null = null;

async function accountToken(): Promise<string> {
  if (account && account.expiresAt - 30 > nowSeconds()) return account.token;
  const { token } = await callGateway("auth/login", {
    method: "POST",
    json: env.catalogAccount,
    schema: z.object({ token: z.string().min(1) }),
  });
  account = { token, expiresAt: expiryOf(token) };
  return token;
}

/** A cached page can't depend on who asked first, so the cache is filled by a ROLE_USER service account. */
async function asCatalogAccount<T>(path: string, options: GatewayRequest<T>): Promise<T> {
  try {
    return await callGateway(path, { ...options, token: await accountToken() });
  } catch (e) {
    if (!(e instanceof GatewayError) || e.status !== 401 || !account) throw e;
    account = null; // e.g. JWT_SECRET was rotated: sign in again, once
    return callGateway(path, { ...options, token: await accountToken() });
  }
}

/** All products by name (the backend's unfiltered list isn't paginated). */
export const getCatalog = unstable_cache(
  async () => {
    const products = await asCatalogAccount("api/v1/products/list", { method: "POST", schema: z.array(CatalogProductSchema) })
      .catch(emptyOnNotFound);
    return { products: products.sort((a, b) => a.name.localeCompare(b.name)), cachedAt: new Date().toISOString() };
  },
  ["catalog"],
  { revalidate: REVALIDATE_SECONDS, tags: [CATALOG_TAG] },
);

export const getCatalogProduct = (id: string) =>
  unstable_cache(
    async () => {
      const product = await asCatalogAccount(`api/v1/products/${seg(id)}`, { schema: CatalogProductSchema }).catch((e) => {
        if (isNotFound(e)) return null;
        throw e;
      });
      return { product, cachedAt: new Date().toISOString() };
    },
    ["catalog-product", id],
    { revalidate: REVALIDATE_SECONDS, tags: [CATALOG_TAG] },
  )();

export const getSpecialties = unstable_cache(
  () => asCatalogAccount("api/v1/healthcareprof/specialties", { schema: z.array(SpecialtySchema) }),
  ["specialties"],
  { revalidate: REVALIDATE_SECONDS, tags: [SPECIALTIES_TAG] },
);
