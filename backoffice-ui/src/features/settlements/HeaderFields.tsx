import { fullName, listPeople } from "@/features/directory/api";
import { Field, Select } from "@/ui/form";

export async function HeaderFields({ value }: { value?: { description: string; settlementDate: string; medicalSalesRepId: string } }) {
  const reps = (await listPeople("msr")).filter((p) => p.active || p.id === value?.medicalSalesRepId);
  return (
    <>
      <Field name="description" label="Description" required maxLength={255} defaultValue={value?.description} />
      <Field name="settlementDate" label="Date" type="date" required defaultValue={value?.settlementDate} />
      <Select name="medicalSalesRepId" label="Sales rep" required defaultValue={value?.medicalSalesRepId ?? ""}
        options={[{ value: "", label: "Choose…" }, ...reps.map((p) => ({ value: p.id, label: fullName(p) }))]} />
    </>
  );
}
