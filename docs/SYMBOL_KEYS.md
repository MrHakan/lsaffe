# CANONICAL SYMBOL KEY LIST

The single source of truth for symbol keys. `SymbolLibrary.kt`
(core-designsystem), `symbols.json` and every `symbolKey` reference in
`equipment_catalogue.json` / `demo_vessel.json` MUST use keys from this list,
spelled exactly. Marker rendering falls back to `APP_GENERIC` for unknown keys.

Format: `KEY | series | English name | Turkish name | mediaTintable`

## LSS — life-saving (green ground)

- LSS001 | LSS | Lifeboat | Can filikası | no
- LSS002 | LSS | Rescue boat | Kurtarma botu | no
- LSS003 | LSS | Liferaft | Can salı | no
- LSS004 | LSS | Davit-launched liferaft | Mataforalı can salı | no
- LSS005 | LSS | Lifebuoy | Can simidi | no
- LSS006 | LSS | Lifebuoy with line | Halatlı can simidi | no
- LSS007 | LSS | Lifebuoy with light | Işıklı can simidi | no
- LSS008 | LSS | Lifebuoy with line and light | Halatlı ve ışıklı can simidi | no
- LSS008_1 | LSS | Lifebuoy with light and smoke | Işıklı ve dumanlı can simidi | no
- LSS009 | LSS | Lifejacket | Can yeleği | no
- LSS010 | LSS | Child's lifejacket | Çocuk can yeleği | no
- LSS011 | LSS | Infant's lifejacket | Bebek can yeleği | no
- LSS012 | LSS | SART | SART | no
- LSS013 | LSS | Survival craft distress signals | Tehlike işaret fişekleri | no
- LSS014 | LSS | Rocket parachute flare | Paraşütlü işaret fişeği | no
- LSS015 | LSS | Line-throwing appliance | Halat atma aygıtı | no
- LSS016 | LSS | Two-way VHF radiotelephone | Çift yönlü VHF telsiz | no
- LSS017 | LSS | EPIRB | EPIRB | no
- LSS018 | LSS | Embarkation ladder | Biniş merdiveni | no
- LSS019 | LSS | Marine evacuation slide | Denize tahliye kaydırağı | no
- LSS020 | LSS | Marine evacuation chute | Denize tahliye tüneli | no
- LSS021 | LSS | Immersion suit | Dalma giysisi | no
- LSS022 | LSS | Liferaft knife | Can salı bıçağı | no

## FES — fire-fighting (red ground)

- FES001 | FES | Fire extinguisher | Yangın söndürücü | yes
- FES002 | FES | Fire hose reel | Hortum makarası | no
- FES003 | FES | Fire locker | Yangın dolabı | no
- FES004 | FES | Fire alarm call point | Yangın alarm butonu | no
- FES005 | FES | Fixed fire-extinguishing battery | Sabit söndürme bataryası | yes
- FES006 | FES | Wheeled fire extinguisher | Tekerlekli söndürücü | yes
- FES007 | FES | Portable foam applicator | Portatif köpük aplikatörü | no
- FES008 | FES | Water fog applicator | Su sisi aplikatörü | no
- FES009 | FES | Fixed fire-extinguishing installation | Sabit söndürme tesisatı | yes
- FES010 | FES | Fixed fire-extinguishing bottle | Sabit söndürme tüpü | yes
- FES011 | FES | Remote release station | Uzaktan salım istasyonu | yes
- FES012 | FES | Fire monitor | Yangın monitörü | yes

## APP — fire-fighting extensions (red ground)

- APP_FIRE_BLANKET | FES | Fire blanket | Yangın battaniyesi | no
- APP_FIRE_HYDRANT | FES | Fire hydrant | Yangın hidrantı | no
- APP_FIRE_HOSE | FES | Fire hose | Yangın hortumu | no
- APP_FIRE_NOZZLE | FES | Fire nozzle | Yangın lülesi | no
- APP_FF_RADIO | FES | Firefighter's radio | İtfaiyeci telsizi | no
- APP_FIRE_ALARM_BELL | FES | Fire alarm bell | Yangın alarm zili | no
- APP_FIRE_ALARM_LIGHT | FES | Fire alarm flashing light | Flaşörlü yangın alarmı | no
- APP_DETECTION_PANEL | FES | Fire detection panel | Yangın algılama paneli | no
- APP_SMOKE_DETECTOR | FES | Smoke detector | Duman dedektörü | no
- APP_HEAT_DETECTOR | FES | Heat detector | Isı dedektörü | no
- APP_FLAME_DETECTOR | FES | Flame detector | Alev dedektörü | no
- APP_GAS_DETECTOR | FES | Gas detector | Gaz dedektörü | no
- APP_FIRE_PUMP | FES | Fire pump | Yangın pompası | no
- APP_EMERGENCY_FIRE_PUMP | FES | Emergency fire pump | Acil yangın pompası | no
- APP_ISC | FES | International shore connection | Uluslararası sahil bağlantısı | no
- APP_SECTION_VALVE | FES | Section valve | Seksiyon valfi | yes
- APP_SPRINKLER | FES | Sprinkler system | Sprinkler sistemi | yes
- APP_CO2_BANK | FES | CO2 cylinder bank | CO2 tüp bataryası | no
- APP_FOAM_SYSTEM | FES | Foam system | Köpük sistemi | no
- APP_INERT_GAS | FES | Inert gas system | İnert gaz sistemi | no
- APP_GALLEY_HOOD | FES | Galley hood system | Kuzine davlumbaz söndürme | no
- APP_FIRE_DOOR | FES | Fire door | Yangın kapısı | no
- APP_FIRE_DAMPER | FES | Fire damper | Yangın damperi | no
- APP_VENT_STOP | FES | Ventilation stop | Havalandırma stobu | no
- APP_QUICK_CLOSING_VALVE | FES | Quick-closing valve | Hızlı kapama valfi | no
- APP_SCBA | FES | SCBA set | Solunum cihazı (SCBA) | no
- APP_FIREMANS_OUTFIT | FES | Fireman's outfit | İtfaiyeci teçhizatı | no
- APP_SAFETY_LAMP | FES | Safety lamp | Emniyet lambası | no
- APP_FIRE_AXE | FES | Fireman's axe | İtfaiyeci baltası | no

## MES — escape (green ground)

- MES001 | MES | Muster station | Toplanma istasyonu | no
- MES002 | MES | Emergency exit (left) | Acil çıkış (sol) | no
- MES003 | MES | Emergency exit (right) | Acil çıkış (sağ) | no
- APP_ARROW | MES | Directional arrow | Yön oku | no
- APP_LLL | MES | Low-location lighting | Alçak konum aydınlatması | no
- APP_ESCAPE_TRUNK | MES | Emergency escape trunk | Acil kaçış trankı | no

## EES — emergency equipment (green ground)

- EES001 | EES | First aid | İlk yardım | no
- EES002 | EES | Emergency telephone | Acil telefon | no
- EES003 | EES | Eyewash | Göz yıkama istasyonu | no
- EES004 | EES | Safety shower | Emniyet duşu | no
- EES005 | EES | Stretcher | Sedye | no
- EES006 | EES | Medical grab bag | Tıbbi acil çantası | no
- EES007 | EES | Oxygen resuscitator | Oksijen resüsitatörü | no
- EES008 | EES | EEBD | EEBD | no
- EES009 | EES | Doctor | Doktor | no
- EES010 | EES | AED | OED (AED) | no
- EES012 | EES | General alarm | Genel alarm | no
- EES013 | EES | Break to obtain access | Erişim için camı kır | no

## APP — machinery, controls, documents, LSA components (slate ground)

- APP_EMERGENCY_GENERATOR | APP | Emergency generator | Acil jeneratör | no
- APP_EMERGENCY_SWITCHBOARD | APP | Emergency switchboard | Acil durum tablosu | no
- APP_BATTERY | APP | Emergency battery | Acil durum aküsü | no
- APP_WATERTIGHT_DOOR | APP | Watertight door | Su geçirmez kapı | no
- APP_SKYLIGHT | APP | Skylight closing device | Aydınlık kapama donanımı | no
- APP_FIRE_CONTROL_PLAN | APP | Fire control plan | Yangın kontrol planı | no
- APP_MUSTER_LIST | APP | Muster list | Role cetveli | no
- APP_DOCUMENT | APP | Document / manual | Doküman / el kitabı | no
- APP_SOPEP | APP | SOPEP locker | SOPEP dolabı | no
- APP_HRU | APP | Hydrostatic release unit | Hidrostatik bırakma ünitesi | no
- APP_PILOT_LADDER | APP | Pilot ladder | Pilot çarmıhı | no
- APP_DAVIT | APP | Davit / launching appliance | Matafora / indirme donanımı | no
- APP_WINCH | APP | Winch | Vinç | no
- APP_RELEASE_GEAR | APP | On-load release gear | Yük altında bırakma donanımı | no
- APP_FALLS | APP | Falls / wire ropes | Fals telleri | no
- APP_ENGINE | APP | Boat engine | Bot motoru | no
- APP_GENERIC | APP | Generic equipment | Genel ekipman | no
