import { Picker } from "../../core/ui/Picker";
import { useHcpDirectory } from "./useHcpDirectory";

/** Healthcare professional picker: active HCPs, searchable, specialty filter chips. */
export function HcpPicker({
  value,
  onChange,
  error,
}: {
  value: string | null;
  onChange: (id: string) => void;
  error?: string;
}) {
  const dir = useHcpDirectory();
  return (
    <Picker
      label="Healthcare professional"
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
