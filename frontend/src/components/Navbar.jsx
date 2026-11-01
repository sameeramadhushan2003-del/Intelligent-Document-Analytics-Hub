import { useState } from "react";
import { Link, useLocation } from "react-router";
import { useAuth } from "../context/authContext.js";
import Icon from "./Icon.jsx";
import ThemeToggle from "./ThemeToggle.jsx";
export function Brand() { return <Link className="brand" to="/" aria-label="Document Hub home"><span className="brand-mark"><Icon name="layers" /></span><span>Document<span className="brand-light">Hub</span><small>INTELLIGENCE, CONNECTED.</small></span></Link>; }
export default function Navbar() {
  const { isAuthenticated } = useAuth();
  const [open, setOpen] = useState(false);
  const { pathname } = useLocation();
  return <header className="marketing-nav"><div className="nav-inner"><Brand /><button className="icon-button mobile-menu" onClick={() => setOpen(!open)} aria-label={open ? "Close navigation" : "Open navigation"} aria-expanded={open} aria-controls="public-navigation"><Icon name={open ? "close" : "menu"} /></button><nav id="public-navigation" className={`marketing-links ${open ? "is-open" : ""}`} aria-label="Main navigation" onClick={() => setOpen(false)}><a href="/#features">Features</a><a href="/#workflow">How it works</a><a href="/#technology">Built with trust</a><span className="nav-divider" /><ThemeToggle />{isAuthenticated ? <Link className="btn btn-primary btn-small" to="/dashboard">Your workspace <Icon name="arrow" size={16} /></Link> : <><Link className={pathname === "/login" ? "active" : ""} to="/login">Sign in</Link><Link className="btn btn-primary btn-small" to="/register">Get started <Icon name="arrow" size={16} /></Link></>}</nav></div></header>;
}
