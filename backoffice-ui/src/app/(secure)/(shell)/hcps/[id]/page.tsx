import { notFound } from "next/navigation";
import { PersonDetail } from "@/features/directory/views";
import { isUuid } from "@/features/schemas";

export default async function Page({ params }: PageProps<"/hcps/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  return <PersonDetail kind="hcp" id={id} />;
}
