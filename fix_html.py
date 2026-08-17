import re
path = r"c:\Users\Ghada\frontend-pfe\project-iq-ui\src\app\demo\components\analyst\matching-page\analyst-matching.component.html"

with open(path, "r", encoding="utf-8", errors="ignore") as f:
    text = f.read()

new_buttons = """<!-- ========= ACTIONS ========= -->
                        <div class="decision-actions mt-5 flex justify-content-center gap-4">
                            <!-- Action unique : Passer a la finalisation dans tous les cas -->
                            <button pButton pRipple icon="pi pi-arrow-right" iconPos="right" 
                                    label="Valider et passer à la Finalisation (Pack APO)"
                                    class="p-button-primary p-button-rounded px-5 py-3 font-bold shadow-2 text-lg hover-lift"
                                    (click)="goToPhase4(true)"></button>
                        </div>"""

text = re.sub(r'<!-- ========= ACTIONS ========= -->\s*<div class="decision-actions.*?</div>\s*</div>\s*</div>\s*</div>', new_buttons + "\n                    </div>\n                </div>\n            </div>", text, flags=re.DOTALL)

with open(path, "w", encoding="utf-8") as f:
    f.write(text)
print("Done!")
