import React from "react";
import { AlertCircle, X } from "lucide-react";

// --- FieldError: red error text below an input ---
export function FieldError({ error }: { error?: string }) {
  if (!error) return null;
  return (
    <p className="mt-1 text-sm text-red-500 flex items-center gap-1" role="alert">
      <AlertCircle className="w-3.5 h-3.5 flex-shrink-0" />
      {error}
    </p>
  );
}

// --- inputErrorClass: adds red border when error exists ---
export function inputErrorClass(error?: string): string {
  return error ? "border-red-500 focus:ring-red-500/50" : "";
}

// --- FormErrorBanner: replaces alert() for API errors ---
export function FormErrorBanner({
  message,
  onDismiss,
}: {
  message?: string;
  onDismiss?: () => void;
}) {
  if (!message) return null;
  return (
    <div
      className="flex items-center gap-3 p-3 mb-4 rounded-xl bg-red-500/10 border border-red-500/20 text-red-500 text-sm"
      role="alert"
    >
      <AlertCircle className="w-4 h-4 flex-shrink-0" />
      <span className="flex-1">{message}</span>
      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          className="p-0.5 hover:bg-red-500/10 rounded"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
}
