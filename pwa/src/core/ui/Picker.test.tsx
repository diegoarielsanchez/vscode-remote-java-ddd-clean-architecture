import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { filterItems, Picker, type PickerItem } from "./Picker";

const items: PickerItem[] = [
  { id: "h1", title: "Ana Gómez", subtitle: "ana@clinic.org", tags: ["Cardiology"] },
  { id: "h2", title: "Bruno Díaz", subtitle: "bruno@clinic.org", tags: ["Pediatrics"] },
  { id: "h3", title: "Carla Ruiz", subtitle: "carla@clinic.org", tags: ["Cardiology", "Pediatrics"] },
];

describe("filterItems", () => {
  it("matches name, email or tag, case-insensitively, and narrows by tag", () => {
    expect(filterItems(items, "bruno", null).map((i) => i.id)).toEqual(["h2"]);
    expect(filterItems(items, "CLINIC", "Cardiology").map((i) => i.id)).toEqual(["h1", "h3"]);
    expect(filterItems(items, "pediat", null).map((i) => i.id)).toEqual(["h2", "h3"]);
  });
});

describe("Picker", () => {
  const renderPicker = (onChange = vi.fn()) =>
    render(
      <Picker label="Healthcare professional" value={null} onChange={onChange} items={items} isLoading={false} loadError={null} onRetry={() => {}} />,
    );

  it("searches and picks with the keyboard", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    renderPicker(onChange);

    await user.click(screen.getByRole("button", { name: /healthcare professional/i }));
    const combobox = screen.getByRole("combobox");
    expect(combobox).toHaveFocus();

    await user.type(combobox, "ruiz");
    expect(screen.getAllByRole("option")).toHaveLength(1);
    await user.keyboard("{Enter}");

    expect(onChange).toHaveBeenCalledWith(items[2]);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("filters by specialty chip and closes on Escape", async () => {
    const user = userEvent.setup();
    renderPicker();
    await user.click(screen.getByRole("button", { name: /healthcare professional/i }));
    await user.click(screen.getByRole("button", { name: "Pediatrics" }));
    expect(screen.getAllByRole("option").map((o) => o.textContent)).toEqual([
      expect.stringContaining("Bruno Díaz"),
      expect.stringContaining("Carla Ruiz"),
    ]);
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
