/* eslint-disable react-refresh/only-export-components */
import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./kc.gen";

// The following block can be uncommented to test a specific page with `yarn dev`
// Don't forget to comment back or your bundle size will increase
// dev is accessible on http://localhost:5173/
// import { getKcContextMock } from "./login/KcPageStory";

// if (import.meta.env.DEV) {
//     window.kcContext = getKcContextMock({
//         pageId: "login.ftl",
//         overrides: {
//             message: {
//                 type: "error",
//                 summary: "This is an error message",
//             },
//             social: {
//                 providers: [
//                     {
//                         loginUrl: "http://localhost:8081/auth/realms/helios-example/broker/github/endpoint",
//                         alias: "github",
//                         providerId: "github",
//                         displayName: "GitHub",
//                         iconClasses: "fa fa-github",
//                     },
//                 ],
//             },
//         }
//     });
// }

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        {!window.kcContext ? (
            <h1>No Keycloak Context</h1>
        ) : (
            <KcPage kcContext={window.kcContext} />
        )}
    </StrictMode>
);
