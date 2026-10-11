import { Directive, ElementRef, HostListener, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

type Ui5Element = HTMLElement & { value?: unknown; checked?: boolean; disabled?: boolean };

/**
 * Bindet ngModel/FormControl an UI5-Eingabefelder. Texteingaben melden Änderungen beim Tippen,
 * Auswahlfelder beim Bestätigen; Checkboxen und Switches arbeiten mit "checked".
 */
@Directive({
  selector: `ui5-input[ngModel], ui5-input[formControlName], ui5-input[formControl],
             ui5-textarea[ngModel], ui5-textarea[formControlName], ui5-textarea[formControl],
             ui5-select[ngModel], ui5-select[formControlName], ui5-select[formControl],
             ui5-date-picker[ngModel], ui5-date-picker[formControlName], ui5-date-picker[formControl],
             ui5-step-input[ngModel], ui5-step-input[formControlName], ui5-step-input[formControl],
             ui5-combobox[ngModel], ui5-combobox[formControlName], ui5-combobox[formControl],
             ui5-checkbox[ngModel], ui5-checkbox[formControlName], ui5-checkbox[formControl],
             ui5-switch[ngModel], ui5-switch[formControlName], ui5-switch[formControl]`,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => Ui5ValueAccessorDirective), multi: true }]
})
export class Ui5ValueAccessorDirective implements ControlValueAccessor {
  private readonly element = inject(ElementRef<Ui5Element>).nativeElement as Ui5Element;
  private readonly tag = this.element.tagName.toLowerCase();
  private onChange: (value: unknown) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  private get isToggle(): boolean {
    return this.tag === 'ui5-checkbox' || this.tag === 'ui5-switch';
  }

  writeValue(value: unknown): void {
    if (this.isToggle) {
      this.element.checked = !!value;
    } else if (this.tag === 'ui5-step-input') {
      this.element.value = value === null || value === undefined || value === '' ? 0 : Number(value);
    } else {
      this.element.value = value === null || value === undefined ? '' : String(value);
    }
  }

  registerOnChange(fn: (value: unknown) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.element.disabled = disabled;
  }

  @HostListener('input')
  handleInput(): void {
    if (this.tag === 'ui5-input' || this.tag === 'ui5-textarea' || this.tag === 'ui5-combobox') {
      this.onChange(this.element.value);
    }
  }

  @HostListener('change')
  handleChange(): void {
    if (this.isToggle) {
      this.onChange(!!this.element.checked);
    } else if (this.tag === 'ui5-step-input') {
      this.onChange(Number(this.element.value));
    } else {
      this.onChange(this.element.value);
    }
  }

  @HostListener('focusout')
  handleBlur(): void {
    this.onTouched();
  }
}
