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
