const fs = require('fs');

const path = 'c:/Users/Ghada/frontend-pfe/project-iq-ui/src/app/demo/components/analyst/matching-page/analyst-matching.component.html';
const content = fs.readFileSync(path, 'utf8');

const lines = content.split('\n');

// We want to keep everything before line 523 (which is index 522)
// and everything after line 626 (which is index 626).
const before = lines.slice(0, 522).join('\n');
const after = lines.slice(626).join('\n');

const newDesign = `        <!-- New Analytical Metrics Layout -->
        <div class="p-4 border-round-2xl surface-card shadow-2 mt-2" style="background: linear-gradient(145deg, #ffffff, #f8fafc); border: 1px solid #e2e8f0;">
            
            <!-- Header -->
            <div class="flex align-items-center gap-3 mb-4 border-bottom-1 surface-border pb-3">
                <div class="w-3rem h-3rem border-round flex align-items-center justify-content-center" 
                     [ngStyle]="{'background': isCompatible() ? '#dcfce7' : '#fee2e2'}">
                    <i class="pi text-2xl" [ngClass]="isCompatible() ? 'pi-verified text-green-600' : 'pi-ban text-red-600'"></i>
                </div>
                <div>
                    <h3 class="m-0 text-xl font-bold text-900">Bilan de Compatibilité</h3>
                    <p class="m-0 mt-1 text-sm text-500">Vue analytique des critères de matching</p>
                </div>
            </div>

            <div class="grid">
                <!-- Les 4 KPI -->
                <div class="col-12 md:col-6 lg:col-3">
                    <div class="p-3 border-round-xl border-1 surface-border surface-ground h-full flex flex-column align-items-center justify-content-center text-center transition-all hover:shadow-1">
                        <span class="text-sm font-semibold text-600 mb-2">Compétences (≥ 50%)</span>
                        <div class="text-3xl font-bold mb-2" [ngClass]="getTauxCompetences() >= 50 ? 'text-green-600' : 'text-red-500'">{{ getTauxCompetences() }}%</div>
                        <i class="pi" [ngClass]="getTauxCompetences() >= 50 ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'"></i>
                    </div>
                </div>

                <div class="col-12 md:col-6 lg:col-3">
                    <div class="p-3 border-round-xl border-1 surface-border surface-ground h-full flex flex-column align-items-center justify-content-center text-center transition-all hover:shadow-1">
                        <span class="text-sm font-semibold text-600 mb-2">Références (≥ 50%)</span>
                        <div class="text-3xl font-bold mb-2" [ngClass]="calculateRefTaux() >= 50 ? 'text-green-600' : 'text-red-500'">{{ calculateRefTaux() }}%</div>
                        <i class="pi" [ngClass]="calculateRefTaux() >= 50 ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'"></i>
                    </div>
                </div>

                <div class="col-12 md:col-6 lg:col-3">
                    <div class="p-3 border-round-xl border-1 surface-border surface-ground h-full flex flex-column align-items-center justify-content-center text-center transition-all hover:shadow-1">
                        <span class="text-sm font-semibold text-600 mb-2">Experts (≥ 40%)</span>
                        <div class="text-3xl font-bold mb-2" [ngClass]="getTauxExperts() >= 40 ? 'text-green-600' : 'text-red-500'">{{ getTauxExperts() }}%</div>
                        <i class="pi" [ngClass]="getTauxExperts() >= 40 ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'"></i>
                    </div>
                </div>

                <div class="col-12 md:col-6 lg:col-3">
                    <div class="p-3 border-round-xl border-1 surface-border surface-ground h-full flex flex-column align-items-center justify-content-center text-center transition-all hover:shadow-1">
                        <span class="text-sm font-semibold text-600 mb-2">Alignement Stratégique</span>
                        <div class="text-lg font-bold mb-2" [ngClass]="isAlignementOk() ? 'text-green-600' : 'text-red-500'">{{ alignementStrategique || 'Non évalué' }}</div>
                        <i class="pi" [ngClass]="isAlignementOk() ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'"></i>
                    </div>
                </div>

                <!-- Final Status Alert -->
                <div class="col-12 mt-3">
                    <div class="p-4 border-round-xl border-1 flex align-items-center gap-4" 
                         [ngClass]="isCompatible() ? 'surface-ground border-green-200' : 'surface-ground border-red-200'">
                        
                        <div *ngIf="isCompatible()" class="flex align-items-center w-full">
                            <div class="w-4rem h-4rem border-circle bg-green-100 flex align-items-center justify-content-center mr-4 flex-shrink-0">
                                <i class="pi pi-check text-green-600 text-3xl"></i>
                            </div>
                            <div>
                                <h4 class="m-0 text-xl font-bold text-green-700 mb-1">Profil 100% Compatible</h4>
                                <p class="m-0 text-green-700 opacity-80 text-sm">Tous les seuils de pré-qualification technique sont franchis avec succès.</p>
                            </div>
                        </div>

                        <div *ngIf="!isCompatible()" class="flex align-items-center w-full">
                            <div class="w-4rem h-4rem border-circle bg-red-100 flex align-items-center justify-content-center mr-4 flex-shrink-0">
                                <i class="pi pi-exclamation-triangle text-red-600 text-3xl"></i>
                            </div>
                            <div>
                                <h4 class="m-0 text-xl font-bold text-red-700 mb-2">Compatibilité Insuffisante</h4>
                                <div class="flex gap-2 flex-wrap">
                                    <p-badge *ngIf="getTauxCompetences() < 50" value="Compétences ({{getTauxCompetences()}}%)" severity="danger"></p-badge>
                                    <p-badge *ngIf="calculateRefTaux() < 50" value="Références ({{calculateRefTaux()}}%)" severity="danger"></p-badge>
                                    <p-badge *ngIf="getTauxExperts() < 40" value="Experts ({{getTauxExperts()}}%)" severity="danger"></p-badge>
                                    <p-badge *ngIf="!isAlignementOk()" value="Alignement" severity="danger"></p-badge>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="mt-4 pt-4 border-top-1 surface-border">
                <div class="decision-actions mt-3 flex justify-content-center gap-4">
                    <button *ngIf="isCompatible()" pButton pRipple icon="pi pi-arrow-right" iconPos="right" 
                            label="Valider et passer à la Finalisation (Pack APO)"
                            class="p-button-success p-button-rounded px-5 py-3 font-bold shadow-2 text-lg hover-lift"
                            (click)="goToPhase4(true)"></button>
                    <button *ngIf="!isCompatible()" pButton pRipple icon="pi pi-arrow-right" iconPos="right" 
                            label="Valider et passer à la Finalisation (Pack APO)"
                            class="p-button-primary p-button-rounded px-5 py-3 font-bold shadow-2 text-lg hover-lift"
                            (click)="goToPhase4(true)"></button>
                </div>
            </div>
        </div>`;

fs.writeFileSync(path, before + '\n' + newDesign + '\n' + after, 'utf8');
console.log("HTML replaced successfully by line numbers!");
