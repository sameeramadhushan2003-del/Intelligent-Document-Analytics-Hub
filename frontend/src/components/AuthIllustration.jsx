import { useTheme } from "../context/useTheme.js";
import "./AuthLayout.css";

export default function AuthIllustration() {
  const { theme } = useTheme();
  const image = theme === "dark" ? "/images/auth-dark.png" : "/images/auth-light.png";

  return (
    <aside className="auth-illustration" aria-hidden="true">
      <img
        src={image}
        alt=""
        width="1672"
        height="941"
        loading="eager"
        decoding="async"
        fetchPriority="high"
      />
    </aside>
  );
}
