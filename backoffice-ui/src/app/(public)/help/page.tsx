import { Card, PageHeader } from "@/ui/components";

// SSG: the same for everyone, no API calls. Built once by `next build`.
export const dynamic = "error";
export const metadata = { title: "Help" };

export default function HelpPage() {
  return (
    <div className="grid gap-6">
      <PageHeader title="Help" />
      <Card title="Signing in">
        <p>Use your company account. You are signed out after a period without activity, and when your session reaches its time limit.</p>
      </Card>
      <Card title="Orders">
        <p>New orders wait for stock and then for approval. An open order page updates by itself while that happens. Orders are placed from the sales rep app.</p>
      </Card>
      <Card title="Quality decisions">
        <p>Administrators release or reject quarantined deliveries and lots. Every decision needs a reason, which is kept in the audit trail.</p>
      </Card>
      <Card title="Catalog">
        <p>Product descriptions may be up to an hour old when they were changed outside this application. Price, stock and lot status are always current.</p>
      </Card>
    </div>
  );
}
