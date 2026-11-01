import { Routes, Route, Outlet, Link } from "react-router";
import ProtectedRoute from "./components/ProtectedRoute.jsx";
import Navbar from "./components/Navbar.jsx";
import AppShell from "./components/AppShell.jsx";
import Home from "./pages/Home.jsx";
import Login from "./pages/Login.jsx";
import Register from "./pages/Register.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import Documents from "./pages/Documents.jsx";
import "./App.css";
function PublicLayout() { return <><Navbar /><Outlet /></>; }
export default function App() {
  return <><a className="skip-link" href="#main-content">Skip to content</a><Routes><Route element={<PublicLayout />}><Route path="/" element={<Home />} /><Route path="/login" element={<Login />} /><Route path="/register" element={<Register />} /></Route><Route element={<ProtectedRoute><AppShell /></ProtectedRoute>}><Route path="/dashboard" element={<Dashboard />} /><Route path="/documents" element={<Documents />} /></Route><Route path="*" element={<main id="main-content" className="session-splash"><h1>This page isn't here.</h1><Link className="btn btn-primary" to="/">Back to home</Link></main>} /></Routes></>;
}
