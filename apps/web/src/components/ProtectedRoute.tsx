import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { isMockDemoSearch } from "../mocks/docMockData";

export function ProtectedRoute() {
  const { user, loading } = useAuth();
  const { search } = useLocation();
  const mockDemo = isMockDemoSearch(search);

  if (loading && !mockDemo) {
    return null;
  }

  if (!user && !mockDemo) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
