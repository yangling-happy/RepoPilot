import { ProductDeployPageView } from "./ProductDeployPageView";
import { useProductDeployPage } from "./useProductDeployPage";

export function ProductDeployPage() {
  const page = useProductDeployPage();
  return <ProductDeployPageView {...page} />;
}
