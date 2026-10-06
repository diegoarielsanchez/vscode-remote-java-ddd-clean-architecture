import { Picker } from "../../core/ui/Picker";
import { useMsrDirectory } from "./useMsrDirectory";

/** Medical sales rep picker: active reps only (visit/settlement services reject inactive ones). */
export function MsrPicker({
  value,
  onChange,
  error,
}: {
  value: string | null;
  onChange: (id: string) => void;
  error?: string;
}) {
  const dir = useMsrDirectory();
  return (
    <Picker
      label="Medical sales rep"
      value={dir.itemFor(value)}
      onChange={(item) => onChange(item.id)}
      items={dir.pickerItems}
      isLoading={dir.isLoading}
      loadError={dir.error}
      onRetry={dir.retry}
      error={error}
    />
  );
}
