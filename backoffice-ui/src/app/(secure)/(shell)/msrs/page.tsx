import { PeopleList } from "@/features/directory/views";

export const metadata = { title: "Sales reps" };

export default async function Page({ searchParams }: PageProps<"/msrs">) {
  const { q, page } = await searchParams;
  return <PeopleList kind="msr" q={typeof q === "string" ? q : undefined} page={typeof page === "string" ? page : undefined} />;
}
