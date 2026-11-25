import { z } from "zod";

/**
 * What the shared ISR cache may hold: master data that is the same for every user. The schemas
 * strip everything else (zod drops unknown keys), so price, stock and the active flag can never
 * reach a cached page, even if the backend adds fields.
 */
export const CatalogProductSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  unit: z.string(),
  gtin: z.string().nullish(),
  lotTracked: z.boolean(),
  storageCondition: z
    .object({ description: z.string().nullish(), coldChain: z.boolean().nullish(), protectFromLight: z.boolean().nullish() })
    .nullish(),
});
export type CatalogProduct = z.infer<typeof CatalogProductSchema>;

export const SpecialtySchema = z.object({ code: z.string(), name: z.string() });

export const CATALOG_TAG = "catalog";
export const SPECIALTIES_TAG = "specialties";
