import { useState, useCallback } from "react";

// --- Rule Types ---
type ValidationRule = {
  message: string;
  validate: (value: any) => boolean;
};

type ValidationRules<T> = {
  [K in keyof T]?: ValidationRule[];
};

type ValidationErrors<T> = {
  [K in keyof T]?: string;
};

// --- Rule Factories ---
export function required(message = "This field is required"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (typeof value === "string") return value.trim().length > 0;
      if (typeof value === "number") return true;
      return value != null && value !== "";
    },
  };
}

export function email(message = "Invalid email address"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true; // skip empty
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
    },
  };
}

export function phone(message = "Invalid phone number"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true; // skip empty
      // Allows: +91 12345 67890, 1234567890, +1-555-000-0000, etc.
      return /^[+]?[\d\s\-()]{7,20}$/.test(value.trim());
    },
  };
}

export function min(minValue: number, message?: string): ValidationRule {
  return {
    message: message || `Must be at least ${minValue}`,
    validate: (value: any) => {
      if (value === "" || value == null) return true; // skip empty — use required() for that
      return Number(value) >= minValue;
    },
  };
}

export function max(maxValue: number, message?: string): ValidationRule {
  return {
    message: message || `Must be at most ${maxValue}`,
    validate: (value: any) => {
      if (value === "" || value == null) return true;
      return Number(value) <= maxValue;
    },
  };
}

export function minLength(len: number, message?: string): ValidationRule {
  return {
    message: message || `Must be at least ${len} characters`,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return String(value).length >= len;
    },
  };
}

export function pattern(regex: RegExp, message: string): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return regex.test(value);
    },
  };
}

export function maxLength(len: number, message?: string): ValidationRule {
  return {
    message: message || `Must not exceed ${len} characters`,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return String(value).length <= len;
    },
  };
}

export function aadhar(message = "Aadhar must be 12 digits starting with 2-9"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return /^[2-9]\d{11}$/.test(String(value));
    },
  };
}

export function pan(message = "PAN must be in format AAAAA0000A"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(String(value));
    },
  };
}

export function ifsc(message = "IFSC must be 4 letters + 0 + 6 alphanumeric"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return /^[A-Z]{4}0[A-Z0-9]{6}$/.test(String(value).toUpperCase());
    },
  };
}

export function indianPincode(message = "Pincode must be 6 digits"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return /^[1-9]\d{5}$/.test(String(value));
    },
  };
}

export function indianMobile(message = "Must be a valid 10-digit Indian mobile number"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      const cleaned = String(value).replace(/[\s\-+]/g, "").replace(/^91/, "");
      return /^[6-9]\d{9}$/.test(cleaned);
    },
  };
}

export function bankAccount(message = "Account number must be 9-18 digits"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return /^\d{9,18}$/.test(String(value));
    },
  };
}

export function pastDate(message = "Date must be in the past"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      const d = new Date(String(value));
      return d < new Date();
    },
  };
}

export function pastOrPresentDate(message = "Date cannot be in the future"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      const d = new Date(String(value));
      const today = new Date();
      today.setHours(23, 59, 59, 999);
      return d <= today;
    },
  };
}

export function minAge(years: number, message?: string): ValidationRule {
  return {
    message: message || `Must be at least ${years} years old`,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      const dob = new Date(String(value));
      const today = new Date();
      const age = today.getFullYear() - dob.getFullYear();
      const monthDiff = today.getMonth() - dob.getMonth();
      const effectiveAge = monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate()) ? age - 1 : age;
      return effectiveAge >= years;
    },
  };
}

export function dateNotBefore(getMinDate: () => string, message = "Date is too early"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      return new Date(String(value)) >= new Date(getMinDate());
    },
  };
}

export function timeAfter(getMinTime: () => string, message = "Must be after start time"): ValidationRule {
  return {
    message,
    validate: (value: any) => {
      if (!value || (typeof value === "string" && value.trim() === "")) return true;
      const minTime = getMinTime();
      if (!minTime) return true;
      return String(value) > minTime;
    },
  };
}

export function custom(fn: (value: any) => boolean, message: string): ValidationRule {
  return { message, validate: fn };
}

// --- Hook ---
export function useFormValidation<T extends Record<string, any>>(rules: ValidationRules<T>) {
  const [errors, setErrors] = useState<ValidationErrors<T>>({});

  const validateField = useCallback(
    (field: keyof T, value: any): string | undefined => {
      const fieldRules = rules[field];
      if (!fieldRules) return undefined;
      for (const rule of fieldRules) {
        if (!rule.validate(value)) {
          return rule.message;
        }
      }
      return undefined;
    },
    [rules]
  );

  const validate = useCallback(
    (values: T): boolean => {
      const newErrors: ValidationErrors<T> = {};
      let isValid = true;
      for (const field of Object.keys(rules) as (keyof T)[]) {
        const error = validateField(field, values[field]);
        if (error) {
          newErrors[field] = error;
          isValid = false;
        }
      }
      setErrors(newErrors);
      return isValid;
    },
    [rules, validateField]
  );

  const clearError = useCallback(
    (field: keyof T) => {
      setErrors((prev) => {
        if (!prev[field]) return prev;
        const next = { ...prev };
        delete next[field];
        return next;
      });
    },
    []
  );

  const clearAllErrors = useCallback(() => {
    setErrors({});
  }, []);

  return { errors, validate, validateField, clearError, clearAllErrors, setErrors };
}
