import { useEffect, useMemo, useState } from "react";
import { healthCareProfApi } from "../api";

const initialCreate = { name: "", surname: "", email: "", specialties: [] };
const initialUpdate = { id: "", name: "", surname: "", email: "", specialties: [], reason: "" };
const initialFilters = { firstName: "", lastName: "", page: 1, pageSize: 10 };

function HealthCareProfessionalPage({ token, isAuthed }) {
  const [filters, setFilters] = useState(initialFilters);
  const [createForm, setCreateForm] = useState(initialCreate);
  const [updateForm, setUpdateForm] = useState(initialUpdate);
  const [actionId, setActionId] = useState("");
  const [rows, setRows] = useState([]);
  const [specialties, setSpecialties] = useState([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("Use the forms below to consume HealthCareProfController.");

  const setSuccess = (text) => setMessage(`OK: ${text}`);
  const setError = (err) => setMessage(`Error: ${err.message || String(err)}`);

  const specialtyMap = useMemo(() => {
    const map = new Map();
    specialties.forEach((s) => map.set(s.code, s.name));
    return map;
  }, [specialties]);

  const loadSpecialties = async () => {
    if (!isAuthed) return;
    try {
      const data = await healthCareProfApi.specialties(token);
      setSpecialties(Array.isArray(data) ? data : []);
    } catch {
      setSpecialties([]);
    }
  };

  useEffect(() => {
    loadSpecialties();
  }, [isAuthed]);

  const toggleSpecialty = (code, target) => {
    const setter = target === "create" ? setCreateForm : setUpdateForm;
    setter((prev) => {
      const exists = prev.specialties.includes(code);
      const next = exists
        ? prev.specialties.filter((item) => item !== code)
        : [...prev.specialties, code];
      return { ...prev, specialties: next };
    });
  };

  const onList = async () => {
    if (!isAuthed) return;
    setLoading(true);
    try {
      const data = await healthCareProfApi.list(token, filters);
      setRows(Array.isArray(data) ? data : []);
      setSuccess(`Loaded ${Array.isArray(data) ? data.length : 0} records`);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  const onCreate = async (event) => {
    event.preventDefault();
    if (!isAuthed) return;
    setLoading(true);
    try {
      await healthCareProfApi.create(token, createForm);
      setCreateForm(initialCreate);
      setSuccess("Health Care Professional created");
      await onList();
    } catch (err) {
      setError(err);
      setLoading(false);
    }
  };

  const onUpdate = async (event) => {
    event.preventDefault();
    if (!isAuthed) return;
    setLoading(true);
    try {
      await healthCareProfApi.update(token, updateForm);
      setUpdateForm((prev) => ({ ...prev, reason: "" })); // a reason belongs to one change
      setSuccess("Health Care Professional updated");
      await onList();
    } catch (err) {
      setError(err);
      setLoading(false);
    }
  };

  const onActivate = async () => {
    if (!isAuthed || !actionId.trim()) return;
    setLoading(true);
    try {
      await healthCareProfApi.activate(token, actionId.trim());
      setSuccess(`Activated ID ${actionId.trim()}`);
      await onList();
    } catch (err) {
      setError(err);
      setLoading(false);
    }
  };

  const onDeactivate = async () => {
    if (!isAuthed || !actionId.trim()) return;
    setLoading(true);
    try {
      await healthCareProfApi.deactivate(token, actionId.trim());
      setSuccess(`Deactivated ID ${actionId.trim()}`);
      await onList();
    } catch (err) {
      setError(err);
      setLoading(false);
    }
  };

  return (
    <section className="space-y-4 animate-enter">
      <div className="card rounded-3xl p-5">
        <p className="text-xs uppercase tracking-[0.22em] text-teal-700">Health Care Professional</p>
        <h2 className="mt-1 font-display text-2xl text-teal-950">HealthCareProfController</h2>
        <p className="mt-2 text-sm text-slate-600">Manage professionals with specialties using mobile-first forms.</p>
      </div>

      <div className="card rounded-3xl p-5">
        <h3 className="font-display text-lg text-teal-900">Search and list</h3>
        <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
          <input className="input" placeholder="firstName" value={filters.firstName} onChange={(e) => setFilters((prev) => ({ ...prev, firstName: e.target.value }))} />
          <input className="input" placeholder="lastName" value={filters.lastName} onChange={(e) => setFilters((prev) => ({ ...prev, lastName: e.target.value }))} />
          <input className="input" type="number" min="1" placeholder="page" value={filters.page} onChange={(e) => setFilters((prev) => ({ ...prev, page: Number(e.target.value) || 1 }))} />
          <input className="input" type="number" min="1" placeholder="pageSize" value={filters.pageSize} onChange={(e) => setFilters((prev) => ({ ...prev, pageSize: Number(e.target.value) || 10 }))} />
        </div>
        <div className="mt-3 grid grid-cols-2 gap-3">
          <button className="btn-primary" onClick={onList} disabled={!isAuthed || loading}>Load list</button>
          <button className="btn-secondary" onClick={loadSpecialties} disabled={!isAuthed || loading}>Load specialties</button>
        </div>
      </div>

      <div className="card rounded-3xl p-5">
        <h3 className="font-display text-lg text-teal-900">Create</h3>
        <form className="mt-3 space-y-3" onSubmit={onCreate}>
          <input className="input" placeholder="name" value={createForm.name} onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))} />
          <input className="input" placeholder="surname" value={createForm.surname} onChange={(e) => setCreateForm((prev) => ({ ...prev, surname: e.target.value }))} />
          <input className="input" type="email" placeholder="email" value={createForm.email} onChange={(e) => setCreateForm((prev) => ({ ...prev, email: e.target.value }))} />
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">Specialties</p>
            <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
              {specialties.map((item) => (
                <label key={item.code} className="chip-select">
                  <input type="checkbox" checked={createForm.specialties.includes(item.code)} onChange={() => toggleSpecialty(item.code, "create")} />
                  <span>{item.name} ({item.code})</span>
                </label>
              ))}
            </div>
          </div>
          <button className="btn-primary w-full" disabled={!isAuthed || loading}>Create</button>
        </form>
      </div>

      <div className="card rounded-3xl p-5">
        <h3 className="font-display text-lg text-teal-900">Update</h3>
        <form className="mt-3 space-y-3" onSubmit={onUpdate}>
          <input className="input" placeholder="id" value={updateForm.id} onChange={(e) => setUpdateForm((prev) => ({ ...prev, id: e.target.value }))} />
          <input className="input" placeholder="name" value={updateForm.name} onChange={(e) => setUpdateForm((prev) => ({ ...prev, name: e.target.value }))} />
          <input className="input" placeholder="surname" value={updateForm.surname} onChange={(e) => setUpdateForm((prev) => ({ ...prev, surname: e.target.value }))} />
          <input className="input" type="email" placeholder="email" value={updateForm.email} onChange={(e) => setUpdateForm((prev) => ({ ...prev, email: e.target.value }))} />
          <div>
            <p className="text-xs uppercase tracking-wide text-slate-500">Specialties</p>
            <div className="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2">
              {specialties.map((item) => (
                <label key={item.code} className="chip-select">
                  <input type="checkbox" checked={updateForm.specialties.includes(item.code)} onChange={() => toggleSpecialty(item.code, "update")} />
                  <span>{item.name} ({item.code})</span>
                </label>
              ))}
            </div>
          </div>
          <textarea className="input" required maxLength={500} rows={2} placeholder="reason for change (required, kept in the audit trail)" value={updateForm.reason} onChange={(e) => setUpdateForm((prev) => ({ ...prev, reason: e.target.value }))} />
          <button className="btn-primary w-full" disabled={!isAuthed || loading}>Update</button>
        </form>
      </div>

      <div className="card rounded-3xl p-5">
        <h3 className="font-display text-lg text-teal-900">Activate / Deactivate</h3>
        <input className="input mt-3" placeholder="id" value={actionId} onChange={(e) => setActionId(e.target.value)} />
        <div className="mt-3 grid grid-cols-2 gap-3">
          <button className="btn-primary" onClick={onActivate} disabled={!isAuthed || loading || !actionId.trim()}>Activate</button>
          <button className="btn-danger" onClick={onDeactivate} disabled={!isAuthed || loading || !actionId.trim()}>Deactivate</button>
        </div>
      </div>

      <div className="card rounded-3xl p-5">
        <h3 className="font-display text-lg text-teal-900">Records</h3>
        {rows.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No records loaded yet.</p>
        ) : (
          <ul className="mt-3 space-y-3">
            {rows.map((prof, idx) => (
              <li key={prof.id || idx} className="record" style={{ animationDelay: `${idx * 60}ms` }}>
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-slate-800">{prof.name} {prof.surname}</p>
                    <p className="text-xs text-slate-500">{prof.email}</p>
                    <p className="mt-1 break-all text-[11px] text-slate-400">{prof.id}</p>
                    <p className="mt-1 text-xs text-slate-500">Specialties: {(prof.specialties || []).map((code) => specialtyMap.get(code) || code).join(", ") || "-"}</p>
                  </div>
                  <span className={prof.active ? "pill-on" : "pill-off"}>{prof.active ? "ACTIVE" : "INACTIVE"}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <p className="rounded-2xl bg-white/70 px-4 py-3 text-sm shadow-soft">{message}</p>
    </section>
  );
}

export default HealthCareProfessionalPage;
