import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { SiteShell } from "./components/siteShell/SiteShell";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { HomePage } from "./pages/homePage/HomePage";
import { ProductDeployPage } from "./pages/productDeploy/ProductDeployPage";
import { ProductDocsPage } from "./pages/productDocs/ProductDocsPage";
import { DocViewPage } from "./pages/docView/DocViewPage";
import { WorkbenchPage } from "./pages/workbench/WorkbenchPage";
import { LoginPage } from "./pages/login/LoginPage";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<SiteShell />}>
          <Route index element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />

          <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<WorkbenchPage />} />
            <Route path="/documentation" element={<ProductDocsPage />} />
            <Route path="/documentation/view" element={<DocViewPage />} />
            <Route path="/deploy" element={<ProductDeployPage />} />
          </Route>

          <Route
            path="/workbench"
            element={<Navigate to="/dashboard" replace />}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
