import type { z } from "zod";
import { Check, Field, TextArea } from "@/ui/form";
import type { Product } from "./api";

export function ProductFields({ product }: { product?: z.infer<typeof Product> }) {
  const s = product?.storageCondition;
  return (
    <>
      <Field name="name" label="Name" required defaultValue={product?.name} />
      <TextArea name="description" label="Description" defaultValue={product?.description ?? ""} />
      <div className="grid gap-4 sm:grid-cols-3">
        <Field name="price" label="Price" inputMode="decimal" required defaultValue={product?.price} />
        <Field name="unit" label="Unit" required defaultValue={product?.unit} />
        <Field name="gtin" label="GTIN" inputMode="numeric" defaultValue={product?.gtin ?? ""} readOnly={!!product?.gtin}
          hint={product?.gtin ? "Can't be changed once set." : undefined} />
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        <Field name="minTemperatureC" label="Min °C" inputMode="decimal" defaultValue={s?.minTemperatureC ?? ""} />
        <Field name="maxTemperatureC" label="Max °C" inputMode="decimal" defaultValue={s?.maxTemperatureC ?? ""} />
        <Field name="maxHumidityPercent" label="Max humidity %" inputMode="numeric" defaultValue={s?.maxHumidityPercent ?? ""} />
      </div>
      <Check name="protectFromLight" label="Protect from light" defaultChecked={s?.protectFromLight ?? false} />
    </>
  );
}
