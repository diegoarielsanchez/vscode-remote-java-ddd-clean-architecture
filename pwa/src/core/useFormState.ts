import { useCallback, useState } from "react";
import type { FieldErrors } from "./http";

/**
 * Form state for ViewModels. Field names match the backend DTO properties, so server-side
 * validation errors ({"visitSiteId": "must not be null"}) map onto fields 1:1.
 */
export function useFormState<F extends object>(initial: F) {
  const [form, setForm] = useState<F>(initial);
  const [errors, setErrors] = useState<FieldErrors>({});

  const setField = useCallback(<K extends keyof F & string>(key: K, value: F[K]) => {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => {
      if (!(key in e)) return e;
      const next = { ...e };
      delete next[key];
      return next;
    });
  }, []);

  return { form, setForm, errors, setErrors, setField };
}
