"use client";

interface TotpInputProps {
    value: string;
    onChange: (val: string) => void;
    onComplete?: (val: string) => void;
    autoFocus?: boolean;
    disabled?: boolean;
}

/**
 * Single numeric input for the 6-digit authenticator code. Styled to match the passcode
 * field on the login screen; supports paste and browser one-time-code autofill.
 */
export function TotpInput({ value, onChange, onComplete, autoFocus, disabled }: TotpInputProps) {
    return (
        <input
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={value}
            autoFocus={autoFocus}
            disabled={disabled}
            onChange={(e) => {
                const val = e.target.value.replace(/\D/g, "").slice(0, 6);
                onChange(val);
                if (val.length === 6) onComplete?.(val);
            }}
            placeholder="000000"
            className="w-full px-3 py-3 bg-[#0D1117] border border-[#21283B] rounded-xl text-white placeholder:text-[#475569] focus:outline-none focus:ring-2 focus:ring-[#FFB300]/40 focus:border-[#FFB300] tracking-[0.5em] text-center text-lg transition-colors disabled:opacity-50"
        />
    );
}
