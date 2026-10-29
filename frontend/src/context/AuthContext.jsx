
import { createContext, useContext, useState } from "react";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [accessToken, setAccessToken] = useState(null);

  const isAuthenticated = Boolean(user && accessToken);

  async function login(email, password) {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });

    const data = await response.json().catch(() => null);

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error("Incorrect email or password.");
      }

      throw new Error(
        data?.message || "Login failed. Please try again."
      );
    }

    if (!data?.accessToken) {
      throw new Error("No access token received from server.");
    }

    // Verify the JWT and retrieve the account details.
    const meResponse = await fetch("/api/auth/me", {
      headers: {
        Authorization: `Bearer ${data.accessToken}`,
      },
    });

    if (!meResponse.ok) {
      throw new Error("Unable to verify your account.");
    }

    const userData = await meResponse.json();

    // Store authentication state in React memory.
    setAccessToken(data.accessToken);
    setUser(userData);

    return userData;
  }

  function logout() {
    setAccessToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return context;
}
