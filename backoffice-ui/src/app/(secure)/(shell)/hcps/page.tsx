import { PeopleList } from "@/features/directory/views";

export const metadata = { title: "Healthcare professionals" };

export default async function Page({ searchParams }: PageProps<"/hcps">) {
  const { q, page } = await searchParams;
  return <PeopleList kind="hcp" q={typeof q === "string" ? q : undefined} page={typeof page === "string" ? page : undefined} />;
}
