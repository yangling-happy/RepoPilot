import { ProductDocsPageView } from "./ProductDocsPageView";
import { useProductDocsPage } from "./useProductDocsPage";

export function ProductDocsPage() {
  const page = useProductDocsPage();
  return <ProductDocsPageView {...page} />;
}
