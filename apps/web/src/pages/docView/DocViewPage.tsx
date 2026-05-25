import { DocViewPageView } from "./DocViewPageView";
import { useDocViewPage } from "./useDocViewPage";

export function DocViewPage() {
  const page = useDocViewPage();
  return <DocViewPageView {...page} />;
}
