import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { Brand } from "./Navbar.jsx";
import Icon from "./Icon.jsx";
import { useAuth } from "../context/authContext.js";
import ThemeToggle from "./ThemeToggle.jsx";
export default function AppShell() {
  const { user, logout, notice } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const profile = useRef(null);
  const menuButton = useRef(null);
  const sidebar = useRef(null);
  const title = pathname === "/documents" ? "My documents" : "Overview";
  function signOut() { logout(); navigate("/login", { replace: true }); }
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement;
    sidebar.current?.querySelector("a")?.focus();
    function keydown(event) {
      if (event.key === "Escape") { setOpen(false); menuButton.current?.focus(); }
      if (event.key === "Tab") {
        const focusable = [...sidebar.current.querySelectorAll("a,button")];
        const first = focusable[0], last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    }
    document.addEventListener("keydown", keydown);
    return () => { document.removeEventListener("keydown", keydown); previous?.focus(); };
  }, [open]);
  return <div className="workspace-shell">
    {open && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} tabIndex={-1} />}
    <aside ref={sidebar} className={`sidebar ${open ? "is-open" : ""}`} role={open ? "dialog" : undefined} aria-modal={open || undefined} aria-label="Workspace navigation">
      <Brand /><button className="icon-button sidebar-close" onClick={() => setOpen(false)} aria-label="Close navigation"><Icon name="close" /></button>
      <span className="sidebar-label">WORKSPACE</span><nav onClick={() => setOpen(false)}><NavLink to="/dashboard"><Icon name="grid" /> Overview</NavLink><NavLink to="/documents"><Icon name="file" /> My documents</NavLink></nav>
      <Link to="/documents#upload" className="sidebar-guide sidebar-quick-action" onClick={() => setOpen(false)}><span className="mini-orb"><Icon name="upload" /></span><span className="sidebar-quick-copy"><strong>Add a document</strong><small>PDF, DOCX or TXT</small></span><Icon name="arrow" size={15} /></Link>
      <Link to="/" className="sidebar-home">Product homepage <Icon name="arrow" size={15} /></Link>
      <button className="sidebar-user" onClick={() => profile.current?.showModal()}><span className="avatar">{user?.fullName?.slice(0, 1).toUpperCase() || "U"}</span><span><strong>{user?.fullName}</strong><small>Personal workspace</small></span><span aria-hidden="true">&bull;&bull;&bull;</span></button>
    </aside>
    <div className="workspace-body"><header className="workspace-topbar"><div className="breadcrumb"><button ref={menuButton} className="icon-button mobile-menu" onClick={() => setOpen(true)} aria-label="Open workspace navigation" aria-expanded={open}><Icon name="menu" /></button><span>Workspace</span><span>/</span><strong>{title}</strong></div><div className="topbar-actions"><span className="private-indicator"><Icon name="shield" size={15} /> Private workspace</span><ThemeToggle /><button className="avatar" aria-label="Open my profile" onClick={() => profile.current?.showModal()}>{user?.fullName?.slice(0, 1).toUpperCase()}</button></div></header>{notice && <p className="notice" role="status">{notice}</p>}<div className="workspace-content" key={pathname}><Outlet /></div><footer className="workspace-footer">Your documents. Your context.<span>Intelligent Document Analytics Hub</span></footer></div>
    <dialog ref={profile} className="profile-dialog"><div className="section-heading"><span className="eyebrow">YOUR ACCOUNT</span><button className="icon-button" onClick={() => profile.current.close()} aria-label="Close profile"><Icon name="close" /></button></div><span className="avatar avatar-large">{user?.fullName?.slice(0, 1).toUpperCase()}</span><h2>{user?.fullName}</h2><p>{user?.email}</p><p className="muted">Account role: {user?.role}</p><button className="btn btn-outline" onClick={signOut}><Icon name="logout" /> Sign out</button></dialog>
  </div>;
}
