"use client";

import { useParams, useSearchParams } from "next/navigation";
import { CompanyDetailPage } from "@/components/company/company-detail-page";

export default function CompanyViewPage() {
    const params = useParams();
    const searchParams = useSearchParams();
    const id = Number(params.id);
    const editMode = searchParams.get("edit") === "true";

    return <CompanyDetailPage companyId={id} initialEditMode={editMode} />;
}
