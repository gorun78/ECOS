/**
 * Route guard — checks for a valid auth token in localStorage.
 * Redirects to /login if not authenticated.
 * @license SPDX-License-Identifier: Apache-2.0
 */
import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";

interface RequireAuthProps {
  children: ReactNode;
}

export default function RequireAuth({ children }: RequireAuthProps) {
  const location = useLocation();
  const token = localStorage.getItem("token");

  if (!token) {
    // Preserve the attempted URL so Login can redirect back after auth
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}
