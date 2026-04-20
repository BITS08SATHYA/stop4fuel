"use client";

import { Modal } from "@/components/ui/modal";
import { BlockingGatePanel } from "./BlockingGatePanel";

interface WhyBlockedModalProps {
    isOpen: boolean;
    onClose: () => void;
    customerId: number | null;
    vehicleId?: number;
    invoiceAmount?: number;
    invoiceLiters?: number;
    onForceUnblockClick?: () => void;
}

export function WhyBlockedModal({
    isOpen,
    onClose,
    customerId,
    vehicleId,
    invoiceAmount,
    invoiceLiters,
    onForceUnblockClick,
}: WhyBlockedModalProps) {
    if (!customerId) return null;
    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Why is this customer blocked?">
            <BlockingGatePanel
                customerId={customerId}
                vehicleId={vehicleId}
                invoiceAmount={invoiceAmount}
                invoiceLiters={invoiceLiters}
                variant="modal"
                onForceUnblockClick={onForceUnblockClick}
            />
        </Modal>
    );
}
