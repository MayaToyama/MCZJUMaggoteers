# 寮€鍙戞棩蹇?/ 鍐崇瓥璁板綍锛坉ev-log锛?
> 鏈枃浠舵寜鏃堕棿鍊掑簭杩藉姞銆傛瘡鏉¤褰曪細**鏃ユ湡 鈫?鍋氫簡浠€涔?鈫?鍐崇瓥涓庡師鍥?鈫?閬楃暀闂**銆?> 鐩殑锛氳鍚庣画鎺ユ墜鐨?AI agent 璇绘噦"涓轰粈涔堟槸杩欐牱璁捐鐨?锛岃€屼笉浠呬粎鏄?鐜板湪闀夸粈涔堟牱"锛堢幇鐘剁湅 `CLAUDE.md`锛夈€?
---

## 2026-08-15 — 同层同档波次 strategy 不放回抽取

- **做了什么**：`RunPlanner` 每层 weak/strong/boss 各自维护已用 strategy id；去重后加权抽；候选空则 `IllegalStateException`（`planError` 终止对局）。单测扩 fixture；`StrongWaveRollTest` 对齐现网 `waves_per_act` 并为 Act1 挂 special。
- **原因**：有放回会出现同档重复关卡；跨档跨层本无同名 strategy。
- **决策**：容量按 **distinct id**（非条目数）；池不够硬失败不兜底；同 seed 仍确定，但相对改前序列会变。
- **遗留**：新 Act1 图若无足够 strong special/全局条目会开局炸；启动期 distinct-id 扫描仍可选（spec §5）。

---
## 2026-08-13 鈥?涓嬫鏀诲嚮寮哄寲绫?empower 鎵撳嚮鐗规晥

- **鍋氫簡浠€涔?*锛氱函寤舵湡 `use_ability`锛堜笅娆℃敾鍑绘秷鑰楋級鍦ㄦ棤 `empower_fx`銆乧ast 浠呬负 `preset: NONE`/缂虹渷鏃讹紝鍛戒腑 victim 鎾唴缃?`deferredStrikeFx`锛圕RIT + DOT_ABOVE + 鏆村嚮闊筹級锛沗playOnEntity` 瀵归潪鐜╁琛ユ挱 sound 骞跺皧閲?NONE銆?- **鍘熷洜**锛氭垬鏂?鍏崄绛夈€屽彸閿搫鍔?鈫?涓嬫鍑烘墜銆嶄笌澶氭鍖?empower 璺緞搴斿榻愶紝鍚﹀垯 buff 妯℃澘鍙湁鏂芥硶闊炽€佸嚭鎵嬫棤鎰熴€?- **鍐崇瓥**锛氫笉寮哄埗鏀?YAML锛涙樉寮?`empower_fx` / 鍙 cast fx 浠嶄紭鍏堛€傚嵆鏃?only 鍖呬粛鏃犻粯璁ゆ墦鍑婚棯銆?- **閬楃暀**锛氫綔鑰呬粛闇€鎵嬪啓 emp/herafinger 绛夊姝ュ唴瀹癸紱娴嬭瘯鏈嶆墜娴嬫垬鏂?鍏崄鍑烘墜闂€?
---

## 2026-08-13 鈥?Multi use_ability + deferred next-hit FX

- **鍋氫簡浠€涔?*锛歶se_ability.effects[] 澶氭鍖?+ legacy 鍗?effect:锛?ireTrigger 鍏堜簬 sweepExpiry锛涘欢鏈熼瓟娉?ireTrigger=expiryTrigger 涓斿懡涓椂璧?weaponPath锛涘嵆鏃舵鍏变韩 TriggerContext锛圖AMAGE_BEAM 鍐?hitTarget锛夛紱cast / empower FX锛坴ictim coalesce锛夛紱WeaponAbilityIds 鍒嗛殧绗﹁鍒欙紱寤舵湡榛樿 stack: REPLACE銆?- **鍘熷洜**锛欵MP/璧媺鑺牸/闆烽渾涔嬫潠绛?lore 闇€瑕佷竴娆″彸閿娈垫垨涓嬫鏀诲嚮澶氭锛涙棫 sweep-first 浣垮悓 trigger 寤舵湡榄旀硶姘歌繙鎵ц涓嶅埌銆?- **鍐崇瓥**锛氬唴瀹?YAML 鏈?PR 涓嶆敼锛堜綔鑰呭悗鏀癸級锛沞ighty_hammer 涓哄敮涓€闈欓粯琛屼负鍙樺寲锛堟棤闇€鏀?YAML 鍗充笅娆¤繎鎴樼湬鏅曪級锛沞mpower锛氭棤鍙 fx 鏃剁函寤舵湡鍖呮挱鍐呯疆鎵撳嚮闂紙涓嶅悆 defaults锛夈€?- **閬楃暀**锛氫綔鑰呭緟鏀?emp / herafinger锛堝惈 held 鏅敾缂撴參锛? 	hunder_rod / mission_sure / rostmourne held锛涙祴璇曟湇鎵嬫祴澶氭涓?empower銆?
---

## 2026-08-12 鈥?娴嬭瘯鏈嶄笂绾垮墠 Critical + Important 淇锛堟壒娆?A/B锛?
- **鍋氫簡浠€涔?*锛氭寜瀹￠槄鏂囨。鎵规 A锛圕1鈥揅5锛? 鎵规 B锛圛4鈥揑7 + I14 YAML 鍚屾锛変慨澶嶏細
  - **C1**锛歚PlayerCombatStats.magicDamagePercentContribution` 璺宠繃 `fireTrigger != null` 鐨勬ā鏉垮眰锛屼粎甯搁┗ grant 璁″叆娉曚激鍊嶇巼銆?  - **C2**锛歚MagicDamageContext` 浠?`HashSet` 鏀逛负 `HashMap<String,Integer>` 寮曠敤璁℃暟锛屽祵濂?`run()` 涓嶅啀鎻愬墠娓呴櫎鏍囪銆傛柊澧炲崟娴?`nestedRunPreservesOuterMark`銆?  - **C3**锛歚MaggoteersGame.startInWorld` 涓栫晫鍒涘缓澶辫触锛坄w==null`锛夊拰鍓ф湰瓒呮椂锛?0s锛変袱澶勫姞 `fail()`锛岀‘淇?`endGame()` 鈫?`cleanupRun()` 涓嶈璺宠繃銆?  - **C4**锛歚RestMenu.openPick` 涓嶅啀棰勬墸璐у竵锛沗PickMenu` 鎺ユ敹 `currencyKind` + `cost`锛屽湪 `RewardService.apply` 鎴愬姛鍚庢墠 `spendOneKind` 鎵ｈ垂銆?  - **C5**锛歚WaveScheduler.onWaveCleared` 閲嶆瀯鈥斺€旂粓灞€娉㈠厛鍙?`clearReward` + `ON_WAVE_CLEAR` + `SummonRegistry.onWaveClear`锛屽啀 `win()`銆?  - **I4**锛歚MobAiLockRegistry` 鏂板 `ENTITY_GAME` 鏄犲皠 + `restoreAll(Object game)` 鎸夊眬鎭㈠锛沗MaggoteersGame.cleanupRun` 鏀逛负 `restoreAll(this)`锛沗onDisable` 淇濈暀鏃犲弬 `restoreAll()` 鍏ㄦ竻銆?  - **I5**锛歚WaveEngine.stop` 涓?MountedSquad 娓呯悊鏀逛负閬嶅巻鏈眬 `ourMobs` 蹇収锛坄removeByRoot`锛夛紝涓嶅啀娓呭叏灞€ `snapshot().keySet()`銆?  - **I6**锛歚ItemInteractRouter` 鐨?`SHOP_EMERALD` handler 鍔?`WaveScheduler.isRestPhase` 闂ㄦ帶锛屾垬鏂楁湡鍙抽敭鎻愮ず"浠呬紤鏁存湡鍙娇鐢?銆?  - **I7**锛歚MaggoteersGame.cleanupRun` 寰幆涓姞 `CooldownService.clear(uuid)`锛岄槻鍚?UUID 杩炲紑涓ゅ眬缁ф壙 CD銆?  - **I14**锛氭簮鐮?`rewards.yml` / `items/maggoteers.yml` / `waves.yml` / `affixes.yml` / `config.yml` / `collectibles.yml` / `items/collectibles.yml` 鍚屾鍒版祴璇曟湇 `E:\MCpaper\plugins\Maggoteers\`銆?- **鍘熷洜**锛氬闃?`docs/review/2026-08-12-娴嬭瘯鏈嶄笂绾垮墠瀹￠槄.md` 寤鸿淇瀹屽啀寮€澶氫汉娴嬶紱鎵规 A 闃诲绋冲畾涓庣粡娴庢纭紝鎵规 B 闃插灞€骞叉壈涓庨厤缃紓绉汇€?- **鍐崇瓥**锛欳2 鐢ㄥ紩鐢ㄨ鏁帮紙闈炴爤寮忥級淇濇寔鍚?tick 鍚?key 璇箟锛汣4 鎵ｈ垂鏀?PickMenu锛堥潪 RewardService锛変繚鎸?RewardService 鏃犲壇浣滅敤锛汭4 鐢?`ENTITY_GAME` map 鑰岄潪鏀归€?LOCKS 缁撴瀯浠ユ渶灏忎镜鍏ャ€?- **閬楃暀**锛氭壒娆?C 鍓╀綑锛圛10 瑙ｉ攣鍟嗗搧銆佹墿鍏?act3_boss锛? 鎵规 D锛堥泦鎴愭祴/鐗堟湰鍙凤級鏈慨锛涙墜娴嬫竻鍗?P0鈥揚3 闇€鍦ㄦ祴璇曟湇瀹炴満楠岃瘉銆?
---

## 2026-08-12 鈥?Boss 姹犵矘婊?+ 浼戞暣璺宠繃鍏ㄥ憳鎶曠エ锛圛1/I2锛?
- **鍋氫簡浠€涔?*锛?  - **I1 Boss 姹犵矘婊?*锛歚WaveScheduler.Cursor` 鍔?`lastBossKillAct`锛沗onWaveCleared` 鍦?`lastTier == "boss"` 鏃舵洿鏂扮矘婊炲眰锛涙柊澧?`bossPoolActIndex(game)`锛沗RestMenu` Boss 姹犳煡璇㈡敼鐢ㄨ鍊兼浛浠ｅ綋鍓嶅眰銆?  - **I2 浼戞暣璺宠繃鍏ㄥ憳鎶曠エ**锛歚Cursor` 鍔?`skipVotes`锛堟瘡浼戞暣鏈熻繘鍏ユ椂娓呯┖锛夛紱`skipRest` 鏀逛负 `voteSkip(game, uuid)`锛堜粎鍐掗櫓妯″紡鐜╁鍙姇銆佸叏鍛樺悓鎰忔墠 `advance`锛夛紱鏂板 `skipVoteCount` / `skipVoteTotal`锛沗RestMenu` 璺宠繃鎸夐挳 lore 鏄剧ず `鎶曠エ杩涘害 x/y`锛屾湭閫氳繃鏃堕噸寮€鑿滃崟鍒锋柊銆?- **鍘熷洜**锛氬榻?CLAUDE 搂9.2锛圔oss 姹?鏈€杩戝嚮鏉€ Boss 鎵€鍦ㄥ眰锛変笌 搂9.3锛堣烦杩囬渶鍏ㄥ憳鍚屾剰锛夛紱琛ョ帺娉曞畬鏁存€с€?- **鍐崇瓥**锛氫笉鍋氬欢闀挎寜閽紙闇€姹傛柟瑁佸噺锛夛紱鎶曠エ浜烘暟浠呮樉绀哄湪鐜版湁璺宠繃鎸夐挳 lore锛屼笉鍔?UI 甯冨眬锛涙姇绁ㄤ粎缁熻鍐掗櫓妯″紡鐜╁锛岃鎴樿€呬笉璁°€?- **閬楃暀**锛歚mvn test` 262 閫氳繃锛汢oss 姹犵矘婊炰笌鎶曠エ闇€娴嬭瘯鏈嶅浜烘墜娴嬶紙P1锛夈€?
---

## 2026-08-12 鈥?娴嬭瘯鏈嶄笂绾垮墠鍏ㄤ粨瀹￠槄鏂囨。

- **鍋氫簡浠€涔?*锛氫骇鍑?`docs/review/2026-08-12-娴嬭瘯鏈嶄笂绾垮墠瀹￠槄.md`锛堣嫳鏂囧埆鍚?`2026-08-12-preflight-review.md`锛夛紱浜ゅ弶鏍搁獙鐢熷懡鍛ㄦ湡/鏁堟灉/閰嶇疆/閮ㄧ讲锛沗mvn test` 261 閫氳繃 / 5 璺宠繃銆?- **鍘熷洜**锛氬唴瀹瑰熀鏈畬鎴愬悗鍑嗗寮€娴嬫湇锛岄渶缁熶竴椋庨櫓娓呭崟涓庡噯鍏ラ棬妲涖€?- **鍐崇瓥**锛欳ritical 浜旈」锛堥瓟鏀绘ā鏉挎硠婕忋€丮agicDamageContext 宓屽銆佸紑灞€澶辫触涓?fail銆? 閫?1 鎵ｈ垂闈炲師瀛愩€佺粓灞€璺宠繃 clearReward锛夊缓璁慨瀹屽啀澶氫汉娴嬶紱娴嬫湇 `rewards.yml`/`items/maggoteers.yml` 涓庢簮鐮佸搱甯屼笉涓€鑷撮』鏄惧紡鍚屾銆?- **閬楃暀**锛氭寜瀹￠槄鏂囨。鎵规 A鈫扗 鎺掓湡淇锛涙墜娴嬫竻鍗曟湭鍏炽€?
---

## 2026-08-12 鈥?Guardian 灏栧埡鍙嶅脊 + 鍒虹尙鑳哥敳 StackOverflow

- **鍋氫簡浠€涔?*锛歚MagicDamageContext` 鍚?tick 瀹堝崼鎵╁睍鍒?`ON_DAMAGE_TAKEN`锛坄EffectListener`锛夛紱鍗曟祴瑕嗙洊 mark 鐢熷懡鍛ㄦ湡銆傚凡閮ㄧ讲娴嬭瘯鏈?jar銆?- **鍘熷洜**锛歝rash 鏍堟槸 `DAMAGE_AREA`鈫抈Guardian.hurtServer` 灏栧埡鍙嶄激鈫掑啀瑙﹀彂 `ON_DAMAGE_TAKEN` 鏃犻檺閫掑綊锛沗waves.yml` 杩滃彜瀹堝崼鑰呬笂鐨?IM `swap` 鍙槸鍚屾€阀鍚堬紝鏍堝唴鏃?InfernalMobs 甯с€?- **鍐崇瓥**锛氫笉绂佺敤 `swap`锛涜ˉ榻愭湰灏卞簲瑕嗙洊 TAKEN 鐨?re-entry 鎶戝埗锛堝師鍏堝彧鎷?`ON_DAMAGE_DEALT`锛夈€?- **閬楃暀**锛氶噸鍚?Paper 鍚庢墜娴嬭繎鎴樺甫鍒?Guardians / 鍒虹尙鑳哥敳涓嶅啀宕╂湇銆?
---

## 2026-08-12 鈥?鑽按娓呴櫎 / 灞€鍐呭厤鐤紙閫€褰瑰亣 amp-255锛?
- **鍋氫簡浠€涔?*锛氬疄鐜扮湡瀹?`clear_potions` / `self_clear_potions`锛坄removePotionEffect`锛変笌濂栧姳 `ADD_POTION` + `immunity: true`锛堜簨浠舵嫤 ADDED/CHANGED + PurifyListener 浼わ級锛涘姞杞界‖澶辫触鍋?255锛涜縼绉?`cataclysm`/`emp`/`holy_water`/`phantasm` 涓?`a3w_imm_*`锛涢儴缃查粯璁ゅ悓姝?`items/*.yml`銆?- **鍘熷洜**锛氬亣 amp-255 缁?PotionMerge 浼氬彉鎴愮湡瀹炴案涔?1-tick 楂樼瓑绾ц嵂姘达紝娌夐粯/鍑€鍖?鍏嶇柅璇箟鍏ㄩ敊銆?- **鍐崇瓥**锛氫笉鏂板 Effect 鏋氫妇锛沗DAMAGE_AREA` 鍥哄畾 damage鈫抍lear鈫抪otions锛汣D 浠呮垚鍔熻娆★紱鏈嶄笂 YAML 闈犳樉寮忓悓姝ワ紙`saveResource(false)` 涓嶈鐩栵級銆?- **閬楃暀**锛氭湇鍐呮墜娴嬫矇榛?鍑€鍖?鍏嶇柅涓庤繃鏈熷亣 YAML 纭け璐ワ紱`affixes.yml` amp 255 浠嶆槸鎬墿璇嶇紑锛堟湰浠诲姟鏈敼锛夈€?
---

## 2026-08-12 鈥?杩戞垬瑙﹀強鏃犻檺锛歴tripDerived 婕忓墺 ENTITY_INTERACTION_RANGE

- **鍋氫簡浠€涔?*锛氭牴鍥犵‘璁ゅ悗淇鈥斺€擿EffectService.stripDerived` / `AuraService.stripAuraDerived` 鏀逛负缁?`DerivedViewAttributes` 鍓ョ瀹屾暣灞炴€у垪琛紙鍚?`entity_interaction_range`锛夛紱`EffectService.resync` 鍦?`WeaponHeldService.sync` 涔嬪悗**濮嬬粓** `resyncDerived`锛堜富鎵嬫湭鍙樻椂 sync early-return 浼氳烦杩囨淳鐢熼噸绠楋級銆?- **鍘熷洜**锛歚resyncDerived` 鐢ㄥ敮涓€ KEY_SEQ 閲嶅姞 modifier锛屼絾 strip 鍙竻 HP/浼ゅ/绉婚€?鏀婚€燂紱瑙﹀強绛夊睘鎬ф瘡娆?apply/鍒囨鍣?鍒版湡鎵熬閮戒細鍙犱竴灞傦紝瀹為檯涓婇檺甯歌〃鐜颁负瀹炰綋杩借釜璺濈锛堢害 48 鏍硷級銆?- **鍐崇瓥**锛氱淮鎶ゆ樉寮?key 鍒楄〃锛堝彲鍗曟祴銆佷笉渚濊禆 RegistryAccess锛夛紱鏂?ADD_ATTRIBUTE 灞炴€у繀椤诲悓姝ヨ繘璇ュ垪琛ㄣ€?- **閬楃暀**锛氶渶閮ㄧ讲鏂?jar 鍚庡眬鍐?`/attribute` 鎴栨墜娴嬭繎鍗亴涓氳Е鍙婁笉鍐嶉殢 resync 鑶ㄨ儉銆?
---

## 2026-08-11 鈥?BOUND_EQUIP 淇濇姢鑳哥敳鍐呭涓?GUI 棰勮

- **鍋氫簡浠€涔?*锛歚RewardOptionIcons` STAT 鍒嗘敮瀵?`BOUND_EQUIP` 璧?`BoundEquipParams.previewLevel` + `ItemService.createItem`锛堜笉缁?collectibles锛夛紱`items/maggoteers.yml` 鏂板 `prot_chest_l1`鈥揱l8`锛堜繚鎶?II鈥揦VI銆? 鎶ょ敳/闊ф€э級锛沗rewards.yml` `act1_strong` 姹犲姞鍏?`prot_chest` STAT锛坄UPGRADE_LEVEL` max 8锛夛紱reference Effect 琛ㄨˉ `BOUND_EQUIP` 琛屻€?- **鍘熷洜**锛歴pec `2026-08-11-bound-equip-protection-design.md` Task 6鈥斺€旈鏉″彲鐜?YAML 鍐呭涓?3 閫?1 棰勮銆?- **鍐崇瓥**锛氬己鎬睜鎶曟斁锛圓ct1 涓悗鏈燂級锛涙棤鎶ょ鏄犲皠锛沗unique: true` 浠呴鎶藉幓閲嶏紝鍗囩骇闈?`RewardDrawVisibility` UPGRADE 璺緞銆?- **閬楃暀**锛氭湇鍐呮墜娴嬬粦瀹?鍐茬獊閿€姣?妲戒綅閿侊紱鍏朵粬灞傛睜鏄惁杩藉姞鍚岀被濂栧姳寰呭唴瀹硅凯浠ｃ€?
---

## 2026-08-08 鈥?DISABLE_AI Effect锛圥hase 1锛?
- **鍋氫簡浠€涔?*锛歚Effect.DISABLE_AI`锛沗MobAiLockRegistry`锛坙onger-wins銆乣EntityRemoveEvent` 鍗歌浇 restore銆?-tick 鎵弿锛夛紱`DisableAiTargets`锛坋nemies|hit_target|attacker + 璺宠繃 Player/`SummonRegistry`锛夛紱`executeEffect`/`executeMagicEffect` 鎺ョ嚎锛涙鍣?`use_ability` 涓?STAT `RewardLoadValidator` 鍔犺浇鐭╅樀锛堝惈 `ON_KILL`+`hit_target` warn+skip锛夈€?- **鍘熷洜**锛氶渶瑕佸彲閰嶇疆鐨勭煭鏆傜槴鐥晫瀵圭敓鐗╋紙鍋?AI锛夛紝閫夋嫨鍣ㄤ笌 BUFF_AREA 鎴樻枟璇箟瀵归綈銆?- **鍐崇瓥**锛氫粎 `setAI`锛涘啀鏂藉姞鍙栨洿闀垮墿浣欙紱鍗歌浇蹇呴』鍏?restore 闃叉案涔?NoAI锛沨eld_effects 鎺ョ嚎寤跺悗 Phase 2銆?- **閬楃暀**锛歅hase 2 held锛涙湇鍐呮墜娴嬪彸閿?AoE / 鍛戒腑鏅?/ 鍙嶉渿鏉ユ簮銆?
---

## 2026-08-08 鈥?涓绘墜姝﹀櫒 held_effects + use_ability ADD_POTION 鍙岃矾寰?
- **鍋氫簡浠€涔?*锛歚WeaponHeldRegistry` / `WeaponHeldService` / `WeaponHeldListener`锛堜富鎵?PDC 鎸傝浇 `held:<itemId>:<n>`锛屽惈 drop/pickup 鍚屾銆乣AuraService.refresh`銆乣:grant` 鍓嶇紑娓呯悊锛夛紱`WeaponEffectValidator` 鍔犺浇鏍￠獙锛沗EffectParamsParser` 鍏变韩 params 瑙ｆ瀽锛堝惈 AURA `grant:`锛夛紱`WeaponTempAttribute` 閲嶅懡鍚嶄负 `WeaponTempEffect` 骞舵敮鎸?expiry ADD_POTION锛沗ItemAbilityRegistry` 瑙ｆ瀽 `potion`/`amp`/`duration_ticks`锛沗EffectService.executeAbility` 鍗虫椂 ADD_POTION 涓?PlayerState expiry 鍒嗘祦銆?- **鍘熷洜**锛歴pec rev.3鈥斺€攔ewardplan 姝﹀櫒闇€瑕佸彸閿?5s 鍔涢噺绛夊嵆鏃惰嵂姘达紝浠ュ強涓绘墜琚姩锛堝父椹?瑙﹀彂鍨嬶級闆?Java 閰嶇疆銆?- **鍐崇瓥**锛歨eld 绂佹 expiry锛堢敓鍛藉懆鏈?鎸佹湁锛夛紱use_ability ADD_POTION 绂佹 expiry+duration 娣风敤锛涜Е鍙戝瀷 held ADD_POTION 蹇呴』 `duration_ticks > 0`锛汚URA held 鐢ㄥ祵濂?`grant:`锛堝悓 rewards 鎴樻棗锛夈€?- **閬楃暀**锛歚BuffPotionParserTest` / `MagicEffectParamsBuffAreaTest` 鍦ㄦ棤 MockBukkit 鐜浠嶄緷璧?`PotionEffectType` 闈欐€佸垵濮嬪寲锛? errors锛岄潪鏈壒鎬у紩鍏ワ級锛涙湇鍐呮墜鍔ㄩ獙璇?swap/drop/鍙抽敭鑽按寰呮祴銆?
---

## 2026-08-06 鈥?瑙﹀彂鍨?ADD_ATTRIBUTE grant 灞?+ REVOKE_GRANTS

- **鍋氫簡浠€涔?*锛歚TriggeredGrantAttribute`锛坄{id}:grant` 鍙犲眰銆乣op: REVOKE_GRANTS` + `source_id`锛夛紱`EffectService` 鍙?pass 鍒嗗彂锛堝悓 trigger 鍏?REVOKE锛夛紱瑙﹀彂鍨?ADD_ATTRIBUTE 鍛戒腑鏃?spawn grant 鑰岄潪鐩存帴鏀?stat锛沗PlayerCombatStats` 浠呰 `fireTrigger=null` 鐨?grant锛沗RewardLoadValidator` 鍏佽 STAT `ON_WAVE_CLEAR`銆佹牎楠?REVOKE 涓?bundle `source_id`锛沗EffectKeys.SOURCE_ID` + `RewardOption` 瑙ｆ瀽 `source_id`锛涘崟娴嬭鐩?grant/revoke/鏍￠獙銆?- **鍘熷洜**锛歴pec rev.2 閫氳繃鈥斺€旈渶绾?YAML 瀹炵幇銆屽彈鍑诲彔榄旀敾 + 娉㈡湯鍙€夋竻绌恒€嶃€屾尝鏈?+5% 榄旀敾銆嶇瓑锛屼笖淇妯℃澘琚璁″叆 `MAGIC_DAMAGE` 鐨?bug銆?- **鍐崇瓥**锛歊EVOKE 娉涚敤浜庝换鎰?attr grant锛堝惈 ATTACK_DAMAGE锛夛紱Bukkit attr grant 鎾ら攢鍚?`resyncDerived`锛沋AML 涓嶈姹?clear 鏉＄洰鎺掑湪 stack 鍓嶏紱`ON_ACT_ENTER` 浠嶄笉鍦?STAT 鐧藉悕鍗曪紙鍚庣画 rewardplan锛夈€?- **閬楃暀**锛氭湰鍦?shell 鏃?JDK 25锛孧aven test 鏈湪鏈幆澧冭窇閫氾紱鏈嶅唴楠岃瘉 bundle 鏍蜂緥锛堥潤姘存祦娑?/ 鐏甸瓊绔嬫柟锛夊緟 rewards.yml 鍐呭浣滆€呰ˉ鏉＄洰銆?
---

## 2026-08-05 鈥?姝﹀櫒绾厤缃€屼笅娆¤繎鎴樺姞鎴愩€?
- **鍋氫簡浠€涔?*锛歚use_ability.effect: ADD_ATTRIBUTE` + 蹇呭～ `expiry`/`stack`锛涘彸閿?`EffectService.apply` 涓存椂鏁堟灉 `ability:<itemId>`锛沗resyncDerived` 鍦?apply锛圓DD_ATTRIBUTE锛変笌 `sweepExpiry` 鍚庢暣琛ㄩ噸鍚屾锛岄伩鍏?modifier 娈嬬暀锛涙牱渚?`maggoteers:power_strike`銆?- **鍘熷洜**锛氬唴瀹归渶瑕併€屽彸閿悗涓嬩竴鍒€浼ゅ鍔犳垚銆嶄笖闆?Java锛涘紩鎿庡凡鏈変簨浠跺埌鏈熸ā鍨嬶紝缂烘鍣ㄨ矾寰勬帴鍏ヤ笌 expiry 娓呭睘鎬с€?- **鍐崇瓥**锛氫粎杩戞垬 `ON_DAMAGE_DEALT`锛涢粯璁?`stack: REPLACE`锛涗笉鍋氫富鏁堟灉+鏃佹寕 grant銆?- **閬楃暀**锛氬紦绠笉娑堣€?涓嶄韩鍙楋紱鏈仛 DAMAGE_AREA 涓庝笅娆″姞鎴愮粍鍚堛€?
---

## 2026-08-05 鈥?ON_DEATH 姣忔鎺夊懡瑙﹀彂 + 姝讳骸鐐?origin

- **鍋氫簡浠€涔?*锛歚MaggoteersDeathStrategy` 鍦ㄨ嚜鍔ㄥ娲讳笌鏈€缁堣鎴樹袱鏉¤矾寰?*涔嬪墠**缁熶竴 `fireTriggerPlayer(ON_DEATH, atEvent(deathLoc))`锛涜嚜鍔ㄥ娲绘垚鍔熷悗鍙﹀彂 `ON_REVIVE`锛沗TriggerContext.eventLocation` + `resolveOrigin()` 渚?`DAMAGE_AREA`/`SUMMON death_site` 绛夊湪鍊掍笅鐐圭粨绠楋紱`RewardLoadValidator` 鐧藉悕鍗曞姞鍏?`ON_DEATH`锛沗rewards.yml` 鏍蜂緥 `death_burst`銆?- **鍘熷洜**锛氶渶姹傘€屾瘡娆℃帀鍛介兘瑙﹀彂銆嶁€斺€斿惈娑堣€?reviveCount 鐨勮嚜鍔ㄥ娲讳笌 reviveCount=0 杞瀵熻€咃紝璇箟涓?`ON_REVIVE`锛堜粎鑷姩澶嶆椿鎴愬姛锛夊垎绂汇€?- **鍐崇瓥**锛氭浜＄偣 = `PlayerDeathEvent` 鏃?`player.getLocation().clone()`锛屽湪浼犻€?鍒囨ā寮忓墠瑙﹀彂锛涘尯鍩熸晥鏋滀笉鍥?`markDown` 鍚?`isAlive=false` 琚?gate锛涘娲诲竵璺緞鏆備笉 fire `ON_REVIVE`锛圥hase D 鍙€夛級銆?- **閬楃暀**锛氬娲诲竵 `ON_REVIVE` 鏈帴锛涙湇鍐呴獙璇?`death_burst` 涓?`SUMMON anchor=death_site` 琛ㄧ幇銆?
---

## 2026-08-05 鈥?GRANT_ITEM / SUMMON Effect锛坮ev.2 spec锛?
- **鍋氫簡浠€涔?*锛氭柊澧?`Effect.GRANT_ITEM`锛圛temCreator 鍙戠墿鍝侊級涓?`Effect.SUMMON`锛堥敋鐐瑰彫鍞?+ PDC 杩借釜锛夛紱`SummonRegistry` 鐢熷懡鍛ㄦ湡锛堟尝娓?杩涘眰/灞€鏈級锛沗SummonExecutor` 鐢熸垚锛沗RewardLoadValidator` 鎵╁睍 trigger 鐧藉悕鍗曪紱姝﹀櫒 `use_ability.consume`锛涚ず渚?YAML锛坒ield_ration / wolf_whistle / fire_orb + rewards 鏍蜂緥锛夈€?- **鍘熷洜**锛歴pec rev.2 瀹℃煡閫氳繃锛岄渶 STAT 琚姩涓庨瓟娉曟鍣ㄥ叡鐢?Effect 绠＄嚎锛屼笖鍙敜鐗╅渶鍙嬩激/鑷縺/娓呯悊绛栫暐銆?- **鍐崇瓥**锛歷alidate 涓?Registry 瑙ｈ€︼紙鍗曟祴涓嶄緷璧?Bukkit Registry锛夛紱鎴樻枟 SUMMON 鍖?`MagicDamageContext`锛沗friendly_fire` 榛樿 false锛涜繘灞傚叏灞€娓呭彫鍞ょ墿锛沜onsume 浠?success 鏃舵墸鐗┿€?- **閬楃暀**锛欵VOKER_FANGS 绛夊疄浣撻渶鏈嶅唴瀹炴祴锛汳ockBukkit 闆嗘垚娴嬫湭琛ャ€?
---

## 2026-07-28 鈥?鍙崌绾т笂闄愰厤缃寲 + 鑱屼笟 bundle grants

- **鍋氫簡浠€涔?*锛歚UpgradeLevelCaps` 涓夌骇瑙ｆ瀽锛坄option.upgrade_max` 鈫?姹?`upgrade_level_cap` 鈫?`config.yml` `rewards.upgrade_level_cap_default`锛夛紱`RewardOption.grants[]` 缁勫悎濂栧姳锛堟鍣?STAT 绛夛級锛沗RewardService.applyBundle`锛沗RewardOptionIcons` GUI 棰勮锛沗act1_weak` cap=2銆乣act1_strong` cap=3锛沗class_vanguard` 绀轰緥 bundle銆?- **鍘熷洜**锛氬師 `UPGRADE_LEVEL` 纭紪鐮?max=4锛屾棤娉曟寜灞?闃舵闄愬埗锛堝 Act1 寮辨尝鐢熷懡鎭㈠浠?II锛夛紱鑱屼笟闇€涓€娆￠€夐」鍚濂栧姳銆?- **鍐崇瓥**锛氭娊姹犲彲瑙佹€т笌 `applyStat` 鍧囪**褰撳墠姹?* cap锛岃法姹犲彲缁х画鍗囩骇锛沚undle 浠呯埗 id 杩?`acquiredUnique`锛沢rant id 榛樿 `{parentId}_g{n}`銆?- **閬楃暀**锛歛ct2/act3 姹?cap 寰呭唴瀹逛綔鑰呰ˉ锛涘祵濂?bundle 涓嶆敮鎸併€?
---

## 2026-07-27 鈥?BUFF_AREA 缁熶竴榄旀硶 Buff

- **鍋氫簡浠€涔?*锛歚Effect.BUFF_AREA` 鍏变韩鏍稿績锛堟鍣?`use_ability` + STAT 鎴樻枟瑙﹀彂锛夛紱`PotionMerge` / `BuffAreaTargets` / `TriggerContext`锛沗EffectListener` 鏀逛负鍗曠帺瀹舵垬鏂楄Е鍙?+ `ON_DAMAGE_TAKEN` 浠呮晫浜烘敾鍑伙紱鏍锋湰 `maggoteers:field_shield` + `a1s_hit_poison` / `a1s_absorb_on_hit`銆?- **鍘熷洜**锛歋pec `2026-07-27-buff-area-unified-design.md` v1.2鈥斺€旈厤缃寕鐘舵€併€佸懡涓笂姣掋€佸彈鍑诲惛鏀讹紝涓庣幇鏈夐瓟娉?FX 浣撶郴瀵归綈銆?- **鍐崇瓥**锛氭鍣?caster FX 鍙湪 handler 鎾竴娆★紱dead entity 璺宠繃鑽按浣嗗厑璁?mark锛沗ON_KILL`/`ON_DAMAGE_DEALT` 鍏ㄩ槦琚姩淇涓轰粎琛屽姩鐜╁锛堝 lifesteal锛夈€?- **閬楃暀**锛氬紦/涓夊弶鎴?`ON_DAMAGE_DEALT` 寤跺悗锛涙案涔?`ADD_POTION` 涓庡悓绫诲瀷 BUFF 鍐茬獊闈犱綔鑰?convention銆?
---

## 2026-07-27 鈥?濂栧姳鎶ょ锛坆uild display collectibles锛?
- **鍋氫簡浠€涔?*锛歚collectibles.yml` + `CollectibleRegistry`/`CollectibleService`锛汼TAT 閫変腑鍙戞斁缁戝畾鎶ょ锛圥DC `collectible`锛夛紱`RewardDrawVisibility` 缁熶竴鎶芥睜锛圫UPPLY 鍙噸澶嶃€乄EAPON/STAT 涓€娆°€乁PGRADE 鏈弧鍙娊锛夛紱`EffectStacker.sweepExpiry` 杩斿洖绉婚櫎 id 鈫?鍚屾鍒犳姢绗︼紱`EffectService.resync` + 澶嶆椿璺緞鍙屽悜 `CollectibleService.resync`锛沗RunItemGuardListener` 绂佹涓㈠純/鏀惧叆瀹瑰櫒锛涘紑灞€瑁呭鏍?`run_gear`锛汫UI锛圥ick/Class/Shop锛夌敤 ItemCreator 棰勮锛涚Щ闄?`rewards.yml` `icon:`锛涙牱鏈?`items/collectibles.yml`銆?- **鍘熷洜**锛歋pec `2026-07-27-reward-collectibles-design.md`鈥斺€旀瀯绛戝彲瑙佹€ч潬鑳屽寘鎶ょ锛岀帺娉曠湡鐩镐粛鍦?`PlayerState`銆?- **鍐崇瓥**锛氭湭鏄犲皠 STAT 浠?warning 涓嶉樆鏂紱婊＄骇 UPGRADE `apply` 杩斿洖 false锛涙姢绗︿笌鏁堟灉 level 鍙屽悜 resync銆?- **閬楃暀**锛氬鏁?STAT 灏氭湭閰嶆姢绗︽潯鐩紱`applyStat` amp 涓?`EffectStacker` 鍙?increment 鎶€鏈€烘湭淇€?
---

## 2026-07-27 鈥?閰嶇疆椹卞姩榄旀硶姝﹀櫒 + 铏氭嫙 MAGIC_DAMAGE

- **鍋氫簡浠€涔?*锛歚use_ability` 閰嶇疆姝﹀櫒锛坄ItemAbilityRegistry` + `ConfigMagicHandler`锛夛紱缁熶竴榄旀硶鏍稿績 `EffectService.executeMagicEffect`锛坄DAMAGE_AREA`/`DAMAGE_BEAM`/`HEAL_AREA` + `PlayerCombatStats` 涔樻暟 + `TargetResolver` + `MagicDamageContext` 闃查€掑綊锛夛紱铏氭嫙 stat `MAGIC_DAMAGE` 鍙繘 `PlayerState` 涓嶅啓 Bukkit锛涘垹闄?`TestBladeHandler`/`ExcaliburHandler`/`HealingStaffHandler`锛涘鍔辨睜绉婚櫎 `a2s_damage_area_interact`锛屾柊澧?`a1s_magic_dmg`/`a2s_magic_dmg`锛沗RewardLoadValidator` 鏍￠獙 fireTrigger 鐧藉悕鍗曘€?- **鍘熷洜**锛歋pec v1.2 鎵瑰噯鈥斺€旀鍣ㄤ笌濂栧姳鍏辩敤浼ゅ鏍稿績銆侀浂 Java 鍔犳鍣ㄣ€佹硶鏈己搴︾嫭绔嬪彔灞傦紱`ON_INTERACT` 濂栧姳涓庢鍣ㄨ矾寰勫啿绐併€?- **鍐崇瓥**锛歚use_ability.cooldown_sec` 涓烘潈濞?CD锛汧X 涓夊眰鍚堝苟锛沗registerHandler` 淇濈暀涓?escape hatch锛泃ier-3 ON_INTERACT 鍏滃簳宸茬Щ闄ゃ€?- **閬楃暀**锛歅5 Lore/璁″垎鏉挎樉绀烘硶鏈己搴︼紱`flame_cleaver` 绛夎繘闃?WEAPON 鏉＄洰寰呭唴瀹逛綔鑰呰ˉ鍏紱娴嬭瘯鏈嶉渶閲嶅惎 + 鍚屾 `items/maggoteers.yml`銆?
---

- **鍋氫簡浠€涔?*锛歚waves.yml` 鏂板 `infernal: { level, affixes }`锛堜富浣?涔樺/浜¤鐙珛閰嶇疆锛夛紱`InfernalMobsBridge` 鍙嶅皠 `mechanizeWithAffixes` + 鍙楃 UUID 杩借釜锛沗MobDeathListener` **LOWEST** 鎻愬墠 `unregisterMob` 鎶戝埗 IM 姝讳骸濂栧姳锛涘垹闄?`AffixTrigger`/`AffixCombatService`/`CombatAffixListener` 鍙?12 涓?`on:` 璇嶇紑涓庨粯璁ゆ尝娆″紩鐢ㄣ€?- **鍘熷洜**锛氬唴瀹圭淮鎶よ€呴渶鍦ㄦ尝娆?YAML 绮剧‘鎸囧畾 IM 鎶€鑳斤紱鍘熺敓 `on:` 鐩戝惉鍣ㄤ笌 IM 鑳藉姏閲嶅彔涓旈毦缁存姢锛涗笉鏀?IM 婧愮爜鍙兘閫氳繃鍙嶅皠 + 姝讳骸鍓嶆敞閿€鎺ュ叆銆?- **鍐崇瓥**锛欼M 涓?`softdepend` 闈?Maven 渚濊禆锛涘彧璋冪敤绮剧‘鍒楄〃鍏ュ彛銆佷笉闅忔満琛ュ叏锛涚鐢?`morph`/`mama`/`mounted`/`vexsummoner`/`ghost`锛汭M 缂哄け/澶辫触闄嶇骇涓烘櫘閫氭€粛璁″叆娉㈡锛涙樉寮?`equipment` 鍦?mechanize 涔嬪悗搴旂敤锛?*涓?*鑷姩鎶婃棫鎴樻枟璇嶇紑杩佺Щ涓?IM 閰嶇疆銆?- **閬楃暀**锛氶粯璁?`waves.yml` 灏氭湭鎵归噺鍔?`infernal:`锛堥渶鍐呭浣滆€呮寜闇€鎵嬪伐娣诲姞锛夛紱娴嬭瘯鏈?smoke 闇€瑁?IM JAR 鍚庨獙鏀躲€?
---

## 2026-07-26 鈥?璇嶇紑鎴樻枟瑙﹀彂閲嶆瀯锛圓ffixTrigger锛夆€?宸?superseded

- **鍋氫簡浠€涔?*锛氬紩鍏?`AffixTrigger` 鏋氫妇锛坄SPAWN` / `hit-player` / `hit-by-player` / `hit`锛変笌 `AffixCombatService`锛沗hit` 鏀逛负鎬彈浠绘剰浼ゅ鏃剁粰鎬嚜韬?buff锛涙柊澧?`hit-by-player` 缁欐敾鍑荤帺瀹跺弽浼わ紱杩佺Щ `poisonous`/`sticky` 閰嶇疆銆?- **鍘熷洜**锛氭棫 `on: hit` 瀹炵幇涓恒€岀帺瀹舵墦鎬?鈫?鐜╁ buff銆嶏紝涓?`hidesuwa` 绛夎璁℃剰鍥剧浉鍙嶏紱瀛楃涓?`on` 鏁ｈ惤涓夊鏃犳牎楠屻€?- **閬楃暀**锛氬悓鏃ヨ InfernalMobs 鎺ュ叆鏂规鍙栦唬骞跺垹闄や笂杩颁唬鐮佽矾寰勩€?
---

## 2026-07-25 鈥?Smoke 绗簩杞紙涔樺 expand + 涓夐€変竴 apply 閾撅級

### 鍋氫簡浠€涔?- **`WaveSpec.expand()`** 澶嶅埗 `passengers`锛堜慨澶嶅己鎬獞涔樻尝瑁稿潗楠戯級锛沗WaveSpecTest` 澧炶ˉ鐢ㄤ緥銆?- **涓夐€?1**锛歊estMenu 鍏?`draw` 鍐嶆墸璐癸紱PickMenu 鎺ユ敹棰勬娊 `offers`锛沗RewardService.apply(player, opt, game)` 杩斿洖 boolean锛岀粦瀹?`PlayerState`锛屽け璐ョ孩瀛?鎴愬姛缁垮瓧锛沗EffectService.apply` 鏄惧紡 game 閲嶈浇銆?- **`GameRegistries.attribute`**锛歭egacy `generic.*` + 1.21 鐭悕鍙岃矾寰勩€?
### 鏂囨。
- Spec/plan锛歚docs/superpowers/specs/2026-07-25-smoke-round2-design.md`銆乣plans/2026-07-25-smoke-round2.md`銆?
### 閬楃暀
- 鏈湴 shell 鏃?`mvn`锛涜鍦?IntelliJ 璺?`mvn test` 鍚庨儴缃叉祴璇曟湇 smoke銆?
## 2026-07-25 鈥?Smoke 淇 + 楠戜箻姝?v1锛坰pec OK 宸插疄鐜帮級

### 鍋氫簡浠€涔?- **AURA**锛歚AttributeModifierKeys` 淇 Paper 26.2 modifier key銆?- **楠戜箻姝?*锛氶€掑綊 `passengers`銆?1tick 鎸傝浇銆乣MountPassengerLimits`/`MountControllerResolver`/`MountedSquadRegistry`+AI锛?tick锛夛紱`waves.yml` 灏稿３楠嗛┘/鍍靛案椹?鐐借冻鍏斤紙鍚祵濂楃偨瓒冲吔濉旓級锛沘ct1鈥? strong 姹犳贩 roll銆?- **澶滆** `player.night_vision`锛?*闅愯韩**绌虹摱澶寸洈锛?*鏃嬮鏂?* CRIT 寮ф铻烘棆 + sweep 闊虫晥銆?
### 鏂囨。
- Spec/plan锛歚docs/superpowers/specs/2026-07-25-smoke-fixes-design.md`銆乣plans/2026-07-25-smoke-fixes.md`銆?
## 2026-07-24 鈥?鏃嬮鏂?FX + 浜斾欢榄旀硶鐗╋紙娉曟潠/鍦ｅ墤/涓夌幆 AURA锛?
### 鍋氫簡浠€涔?- **鏃嬮鏂?* `maggoteers:test_blade`锛歚SPIRAL_RADIUS` + `ANGRY_VILLAGER` + `ENTITY_RAVAGER_ROAR`锛坕tems + `magic_fx.weapons`锛夈€?- **ItemCreator**锛歚healing_staff`锛堥捇鐭崇煕妯″瀷鏈ㄦ锛夈€乣excalibur`锛沗HealingStaffHandler`锛?5s/5鏍?+3蹇冿級銆乣ExcaliburHandler`锛?0s/鍑嗘槦闀垮害鍏夋潫+璺緞浼ゅ锛夛紱`MaggoteersPlugin` 娉ㄥ唽 handler銆?- **涓夐€変竴 AURA**锛歚a1s_ring_strength`锛?鏍?+4 鏀伙紝`THICK_RING`+ENCHANT锛夈€乣a1s_ring_regen`锛?鏍煎啀鐢燂紝`SMOOTH_EXPAND_RING`锛夈€乣a1s_slow_field`锛?0鏍?-30% 绉婚€?+ `mark_head`/`DOT_ABOVE`锛夛紱姝﹀櫒濂栧姳 `a1s_healing_staff` / `a1s_excalibur` 鍏?`act1_strong` 姹犮€?
### 鍐崇瓥涓庡師鍥?- 涓诲姩姝﹀櫒璧?tier-2 handler + `use_fx`锛涜鍔ㄧ幆璧?AURA + `carrier_fx`/`mark_fx` 涓?`AuraService` 宸叉湁鍒锋柊閾俱€?
### 閬楃暀
- 鏈湴鏈窇 `mvn test`锛堢幆澧冩棤 mvn锛夛紱涓婃湇鍚?`/maggoteers debug give` 瀹炴満楠岀矑瀛愪笌闊虫晥銆?
## 2026-07-24 鈥?AURA 鍥㈤槦/瀵规晫鍏夌幆锛坴1 宸插疄鐜帮級

### 鍋氫簡浠€涔?- `Effect.AURA`銆乣AuraService.refresh`锛?s锛夈€佸弸/鏁?grant銆丠EAL 鑴夊啿銆乣RewardOption` grant/expiry 瑙ｆ瀽銆?- 绀轰緥锛歚a1s_war_banner` / `a1s_medic_field` / `a1s_miasma`锛沗AuraParamsTest`銆?- 璁″垝锛歚docs/superpowers/plans/2026-07-24-team-aura-effect.md`銆?
### 鍐崇瓥涓庡師鍥?- 娲剧敓 buff 鍦ㄦ帴鏀惰€呭疄浣擄紱鎼哄甫鑰呮浜?`stripAllFromSource`锛涘眬鏈?`clearGame`銆?
### 閬楃暀
- 灞€鍐呭疄娴嬶紱`debug aura`锛涙€墿鍑哄湀绮剧‘ removePotionEffect銆?
## 2026-07-24 鈥?涔樺鎬?/ 璋冭瘯 / 澶滃ぉ / 杩涘眰鍑嗗 / 榄旀硶 FX

### 鍋氫簡浠€涔?- **waves.yml `passengers[]`**锛歚StepCfg`/`SpawnStep`/`MobFactory.spawnStepGroup`锛涗箻瀹㈣蛋涓庝富浣撶浉鍚岀殑 coeff脳affix脳scaling銆?- **璋冭瘯**锛歚debug jumpto|jump|spawnmob|effects`锛沗WaveScheduler.debugSeekWave/debugShiftWave`銆?- **`world.time_lock: night`**锛堥粯璁わ級锛屽績璺冲唴閲嶈鏃堕棿闃叉棩鐓с€?- **`act_enter.prep_sec: 10`**锛氳繘灞?Phase `PREP` 鍚庡啀寮€娉€?- **`magic_fx` + `use_fx`**锛氬叚绉嶇矑瀛愰璁?+ 鍔犵矖鍦嗙幆锛沗TestBladeHandler` 璧?`MagicFxService`銆?- 璁捐鏂囨。锛歚docs/superpowers/specs/2026-07-24-gameplay-polish-design.md`銆?
### 鍐崇瓥涓庡師鍥?- 涔樺鍗曠嫭杩借釜 UUID锛屼笌 WaveEngine 姝讳骸/鎵熬涓€鑷淬€?- 璋冭瘯璺虫尝娓呮€苟鐩存帴 `beginWave`锛岃烦杩囦紤鏁翠笌杩涘眰 PREP銆?- 榄旀硶 FX 棰勮鐢ㄧ煭浠诲姟鍔ㄧ敾锛圫PIRAL/RIPPLE/SMOOTH锛夛紝鍙傛暟鍙厤缃?radius/ray/density銆?
### 閬楃暀
- 鏈湴闇€ `mvn test` 楠岃瘉锛汸aper `Registry.SOUNDS` 鑻ョ紪璇戞姤閿欏彲閫€鍥炵函 `Sound.valueOf`銆?- 绀轰緥楠嗛┘+涔樺 strategy 灏氭湭鍐欏叆榛樿 `waves.yml`锛堜粎 schema 鏀寔锛夈€?
## 2026-07-24 鈥?娉㈡ delay 璇箟 + 鍓ф湰 RNG / 鍙娴嬫€?
### 鍋氫簡浠€涔?- **`delay` 鏀逛负姝ュ墠绛夊緟锛堢浉瀵逛笂涓€姝ワ級**锛歚WaveSpec.expand()` 鎸?steps 椤哄簭涓?`repeat` 绱姞鏃堕棿杞达紝涓嶅啀浣跨敤銆岃窛鏈尝寮€濮嬬殑缁濆 delay + period 琛ラ棿銆嶃€俙RunPlanner` 涓嶅啀鎸?`delaySec` 鎺掑簭 steps锛屼繚鐣?`waves.yml` 涔﹀啓椤哄簭銆?- **寮烘€?roll 鐙珛鎬?*锛歚RunPlanner` 鐢?`rollSalt(tier,index)` 娲剧敓 RNG锛岄伩鍏嶆棫 `derive(200+i)` 璺ㄥ眰鎾炶溅瀵艰嚧澶氭尝鍏辩敤鍚屼竴闅忔満搴忓垪銆?- **`WaveSpec.strategyId`**锛氬墽鏈啓鍏ユ墍 roll 鐨?strategy id锛涘紑鎴樺箍鎾笌 **`/maggoteers debug plan`** 杈撳嚭姣忓眰姣忔尝 `[搴忓彿:tier=id]`锛屼究浜庢牳瀵广€屽叏鍥?strong 閮芥槸 s1_crp_many銆嶇被闂銆?- **`SeededRng.weightedIndex`**锛氳烦杩?weight鈮? 鐨勬睜鏉＄洰锛沗WaveDefinitions` 娣辨嫹璐?pool 鍒楄〃闃茶鏀广€?- **閰嶇疆**锛歚waves.yml` act3 鍘绘帀閲嶅 `weak:` 閿紱璧勬簮娉ㄩ噴鏍囨槑 delay 璇箟銆?- **鍗曟祴**锛歚WaveSpecTest` 瀵归綈鏂板睍寮€鏃堕棿杞达紱鏂板 `StrongWaveRollTest`锛堝己鎬睜澶氭牱鎬э紝闇€鏈湴 `mvn test`锛夈€?
### 鍐崇瓥涓庡師鍥?- 绛栧垝鍙ｅ緞銆屾鍓嶅欢鏃躲€嶄笌 repeat 閾惧紡绱姞涓€鑷达紱`s1_crp_many` 绛夎嫢闇€杞笌杞箣闂寸殑绌烘。锛屽簲鎶?*璇ヨ疆棣?step 鐨?delay** 閰嶅ぇ锛岃€屼笉鏄紩鎿庣敤 period 纭ˉ 1 tick銆?- strategyId + debug plan 鎶娿€屾睜瀛愰厤缃?vs 鍓ф湰 vs 鍒锋€€嶆媶寮€锛岄伩鍏?repeat 鏃跺簭 bug 涓?roll bug 娣峰湪涓€璧锋帓鏌ャ€?
### 閬楃暀
- 姝ュ墠璇箟涓?`s1_crp_many`锛坮epeat:3銆侀姝?delay:0锛夌浜岃疆浼氫笌绗竴杞湯姝ュ悓 tick 鍙犲埛锛涜嫢闇€闂撮殧锛屽湪 waves.yml 鎶?repeat 杞姝?delay 鏀逛负 6锛堟垨鍗曠嫭 strategy锛夈€?- 鏀?`waves.yml` 鍚庨』**閲嶅惎鏈?*锛坄WavesConfig` 浠?onEnable 鍔犺浇锛夛紱閮ㄧ讲 jar 寤鸿 `mvn clean package`銆?- in-game 楠岃瘉锛氭柊 jar + `/maggoteers debug plan` 寮烘€?strategy 鍒楄〃銆?
---

## 2026-07-24 鈥?ItemCreator 1.1.0锛圥aper 26.2 main锛? GameRules 鏂板悕

### 鍋氫簡浠€涔?- 浠?`mczju-ops/MCZJUItemCreator` **main**锛堟棤鐙珛 `26.2` 鍒嗘敮锛沗api-version: 26.2`锛岀増鏈?**1.1.0**锛夋湰鍦?`mvn package`/`install`锛宩ar 宸插鍒跺埌 `E:\MCpaper\plugins\`銆?- Maggoteers `pom` 鈫?`MCZJUItemCreator:1.1.0`锛沗plugin.yml` `api-version: 26.2`銆?- `WorldService`锛氬純鐢?`GameRule.DO_*` 鈫?`GameRules.ADVANCE_TIME` / `ADVANCE_WEATHER` / `NATURAL_HEALTH_REGENERATION` / `SPAWN_MOBS`锛涘苟璁剧櫧澶?鏅村ぉ銆?
### 鍐崇瓥涓庡師鍥?- 杩滅▼浠呮湁 `main`锛汸aper 26.2 閫傞厤宸插悎鍏?main锛屾寜 1.1.0 浣跨敤銆?- 缂栬瘧 warning 瀹炰负 GameRule 寮冪敤锛堥潪 ItemCreatorApi 绛惧悕鍙樺寲锛汚PI 涓?1.0.1 鍏煎锛夈€?
### 閬楃暀
- in-game 鍐掔儫锛欼temCreator 鍔犺浇 + Maggoteers 鐗╁搧鍙戞斁銆?
---

## 2026-07-24 鈥?绋€鐤忓埛鎬偣鍥為€€ boss锛圙3 鏀惧锛?
### 鍋氫簡浠€涔?- `MapPoints.resolvePointOrBossFallback`锛氶潪 boss 鐐规暟灏戜簬 9 涓斿紩鐢ㄧ己澶辩偣 鈫?鐢?`boss` 鍧愭爣锛涘凡婊?9 闈?boss 鎴栫己 boss 浠?fail-fast銆?- `RunPlanner` 鏀圭敤璇ヨВ鏋愶紱鍗曟祴瑕嗙洊鍥為€€ / 婊?9 浠嶆姏閿欍€?
### 鍐崇瓥涓庡師鍥?- 鍦板浘鍙彧閰?1鈥? + boss锛岃€?`waves.yml` 浠嶅彲鍏辩敤寮曠敤 1鈥? 鐨?strategy锛岄伩鍏嶆瘡鍥鹃噸鍐欐尝娆°€?
### 閬楃暀
- ItemCreator 26.2 绉佹湁浠撴湰鏈烘棤 SSH/HTTPS 鏉冮檺锛屽皻鏈湰鍦版瀯寤轰笌淇緷璧?warning銆?
---

## 2026-07-24 鈥?Plan 10锛氬崟缁撴瀯 NBT 鍦板浘鍔犺浇

### 鍋氫簡浠€涔?- `MapEntry`锛歚hasStructure` 鍙栦唬鍥涜薄闄?`nbtFiles`锛涙帰娴?`structure.nbt`銆?- `StructurePaster`锛氬眰鍘熺偣鍗曟绮樿创锛涚己鏂囦欢浠?64脳64 鐜荤拑鍏滃簳銆?- `CLAUDE.md` 搂5 涓庣浉鍏炽€? 璞￠檺銆嶈〃杩板悓姝ャ€?
### 鍐崇瓥涓庡師鍥?- 纭垏鎹€佸浐瀹氭枃浠跺悕銆佸師鐐硅瀵归綈鈥斺€斾笌缁撴瀯鏂瑰潡宸ヤ綔娴佷竴鑷达紝灏戦厤缃垎鏀€?- 涓嶄繚鐣欏洓璞￠檺鍏煎锛涚ず渚嬬浉瀵瑰潗鏍囧湪鏈夌湡瀹?NBT 鍚庨渶鎸夌粨鏋勫師鐐归噸鍐欍€?
### 閬楃暀
- 杩愮淮瀵煎嚭骞舵斁缃悇鍥?`structure.nbt`锛涙寜缁撴瀯閲嶅啓 `points.yml`銆?- in-game锛氭湁/鏃?nbt 涓ょ璺緞鍐掔儫銆?
---

## 2026-07-24 鈥?Plan 9锛歅aper 26.2 + MGC 1.0.7 鍗囩骇锛圛temCreator 鏆備笉瑁咃級

### 鍋氫簡浠€涔?- **鏋勫缓鍩虹嚎**锛歚pom.xml` 鈫?Paper `26.2.build.62-beta`銆丮GC `1.0.7`銆丣DK **25**锛沗plugin.yml` `api-version: 1.21`銆?- **`GameRegistries`**锛氶泦涓?Registry 瑙ｆ瀽鑽按/灞炴€?瀹炰綋锛堟浛浠?`getByName`/`valueOf`锛夈€?- **閮ㄧ讲**锛歚E:\MCpaper\plugins\` 鏀剧疆 `MCZJUGameCore-1.0.7.jar` + 鏂版瀯寤?`Maggoteers-*.jar`锛?*鏃?ItemCreator**锛堝喅绛?C锛夈€?- **鏋勫缓鑴氭湰**锛歚scripts/build-with-jdk25.sh`锛圵SL 闇€閰嶅悎 Windows JDK25锛沇indows 鎺ㄨ崘 IDEA `mvn.cmd` + `%APPDATA%\.minecraft\runtime\java-runtime-epsilon`锛夈€?
### 鍐崇瓥涓庡師鍥?- **ItemCreator 浠?compile-time**锛氭祴璇曟湇涓嶈锛沗ItemService` warning + `RewardService` 鐗╁搧 warning 鍙帴鍙楋紱QA 渚ч噸 STAT/娉㈡/鑿滃崟/MGC銆?- **JDK 25 寮哄埗**锛歅aper 26.2 / MGC 1.0.7 涓?class file 69锛汮DK 21 鏃犳硶缂栬瘧銆傛湰鍦扮敤 Minecraft 鑷甫 JDK 25 璺?Maven銆?
### 閬楃暀
- **in-game 鍐掔儫**锛圥lan 9 Task 6 Step 4锛夛細join 鈫?娉㈡ 鈫?鑿滃崟 鈫?缁撶畻銆?- **ItemCreator** 鎭㈠鍚庡彟娴?WEAPON/SUPPLY/璐у竵銆?- **Windows 鏂扮幆澧?*锛氶渶 `mvn install:install-file` 瀹夎 `MCZJUItemCreator-1.0.1.jar`锛堟垨浠?WSL `~/.m2` 鎷疯礉锛夋墠鑳界紪璇戙€?
---

## 2026-07-06 鈥?Plan 8 瀹炵幇锛氳皟璇曞懡浠?+ 閰嶇疆鏍￠獙 + 璇嶇紑鑽按 + 鏀跺熬锛? 浠诲姟 SDD锛?
### 鍋氫簡浠€涔?- 鎸?Plan 8 鐢?SDD 钀藉湴 4 浠诲姟锛?  1. **璋冭瘯鍛戒护**锛歚/maggoteers` 濂椾欢锛坰tate/plan/wave/give/coin銆佸己鍒?act/wave 璺宠浆绛夛級锛屼緵 in-game 璋冭瘯涓?QA銆?  2. **閰嶇疆 schema 鏍￠獙**锛氬惎鍔?reload 鏃跺 waves/rewards/affixes 绛夊仛寮曠敤涓庣粨鏋勬牎楠岋紝缂哄け strategy 姹犲紩鐢?fail-fast锛宎ffix/item 寮曠敤 warn/severe銆?  3. **璇嶇紑鑽按**锛歚MobFactory` 搴旂敤 `SpawnStep.potions()` 缁欐€紱`CombatAffixListener` 澶勭悊 `on: hit-player` 绫昏瘝缂€锛堝 toxic/vampiric锛夈€?  4. **鏀跺熬**锛歚RewardService.allPoolIds()` 杩斿洖 `Collections.unmodifiableSet`锛涙湰 dev-log 鏉＄洰銆?
### 鍐崇瓥涓庡師鍥?- **AffixService 鍏堜簬 WavesConfig 鍔犺浇**锛歐avesConfig 鏍￠獙 strategy 鍐?affix 寮曠敤鏃堕渶 AffixService 宸插氨缁€?- **strategy 姹犲紩鐢?fail-fast锛宎ffix/item 寮曠敤 warn/severe only**锛氭睜/strategy 缂哄け浼氬鑷磋繍琛屾椂鏃犳硶寮€灞€鎴?roll 娉㈡锛屽繀椤荤‖澶辫触锛沘ffix/item 寮曠敤闂鍙檷绾т负鏃ュ織鍛婅锛岄伩鍏嶅崟鏉￠厤缃?typo 鎷栧灝鏁存湇 reload銆?
### 閲岀▼纰?- **Plan 1鈥? 鍏ㄩ儴瀹屾垚**锛堜粠涓栫晫/娉㈡/鏁堟灉/鑿滃崟/鎸佷箙鍖?鍟嗗簵鍒拌皟璇曚笌鏍￠獙锛夈€?
### 閬楃暀
- **闇€瑕?in-game QA**锛歞ebug 鍛戒护銆?toxic/vampiric 绛?hit-player 璇嶇紑銆侀厤缃?fail-fast 琛屼负銆?- **鍐呭杩愮淮**锛歮aps/NBT 璧勪骇濉厖涓庡湴鍥句笓灞炴尝娆°€?- **鍙€夊悗缁?*锛氶€氬叧鏃堕棿姒滐紙鍙︽敞鍐?Leaderboard锛夈€佸晢搴?AlertMenu 浜屾纭銆?
---

## 2026-07-04 鈥?Plan 7 瀹炵幇锛氭寔涔呭寲 + 灞€澶栧晢搴?+ 瑙ｉ攣鍥炴祦 + 鎺掕姒滐紙5 浠诲姟 SDD锛?
### 鍋氫簡浠€涔?- 鎸?`docs/plan/2026-07-04-plan7-persistence-shop-unlock-leaderboard.md` 鐢?SDD 钀藉湴 5 浠诲姟锛堟瘡浠诲姟 implementer+reviewer锛屽叏閮?Approved锛夛細
  1. **`MaggoteersPlayerData extends JsonPlayerData`**锛坆alance/totalEarned/unlocks + grant/spend/unlock锛? `Settlement` 绾嚱鏁帮紙WIN=winFlat, FAIL=failPerAct脳acts+failPerWave脳waves锛? 4 渚嬪崟娴?+ `registerPlayerData` + config settlement 娈点€?  2. **缁撶畻**锛歚cleanupRun` 鎹?`outcome`(WIN/FAIL) 鈫?`Settlement.grant` 鈫?姣忎汉 `getData(MaggoteersPlayerData.class).grant(grant)`锛坰etModified 钀界洏锛夈€俙WaveScheduler.progress(game): int[]` 鏆撮湶 actIndex+waveIndex銆?  3. **灞€澶栧晢搴?*锛歚UnlockShopMenu`锛堝晢鍝?鎵€鏈?`requires_unlock:true` 閫夐」锛岃姳 `balance` 瑙ｉ攣鈫抈unlocks.add`锛? `/maggoteers shop` CommandExecutor + plugin.yml 娉ㄥ唽銆?  4. **瑙ｉ攣鍥炴祦**锛歚UnlockRegistry.unlocksOf(p)`锛堣鎸佷箙 unlocks 闃插尽鎬ф嫹璐濓級+ `RewardService.draw` 鍔?`requiresUnlock && !unlocked.contains(id) 鈫?continue` 杩囨护锛堝凡瑙ｉ攣鎵?roll 鍒帮級銆?  5. **鎺掕姒?*锛歚MaggoteersTotalLeaderboard extends PlayerDataLeaderboard`锛坄getFieldName`鈫?totalEarned", 榛樿闄嶅簭锛? `registerLeaderboard("maggoteers_total", ...)`銆?
### 鍐崇瓥涓庡師鍥?- **`getData` 灞€澶栧彲鐢?*锛堟牳瀹?MGC 婧愮爜锛夛細`PlayerExt.getData(Class)` 濮旀墭 `PlayerDataManager.getPlayerData(uuid, class)`锛屾寜 gameId+UUID 瀛樺彇锛?*涓?ProfileManager 瀹屽叏鐙珛**鈥斺€斿眬澶栧晢搴楋紙鐜╁涓嶅湪灞€閲岋級涔熻兘姝ｇ‘璇?balance/unlocks銆俙cleanupRun` 鐨?settlement 鍦?`switchProfile(null)` 鍚庤窇涔熷畨鍏紙鍚屾簮锛夈€?- **SortOrder 榛樿闄嶅簭**锛堟牳瀹?javap 瀛楄妭鐮侊級锛歚AbstractLeaderboard.getSortOrder()` 杩斿洖 `DESCENDING`鈥斺€旂疮璁″崼鎴嶅竵澶╃劧闄嶅簭锛屾棤闇€ override銆?- **缁撶畻鏀?cleanupRun 鍓嶆**锛圤N_GAME_END fire 涔嬪悗銆乄aveScheduler.stop 涔嬪墠锛夛細姝ゆ椂 `getPlayers()` 浠嶅湪銆乧ursor 杩樺湪銆乬etData 鍙敤銆傚悗娈?switchProfile 涓嶅奖鍝?getData銆?
### 閬楃暀锛圥lan 8锛?- `/maggoteers` 鍛戒护濂椾欢锛坉ebug state/plan/wave/give/coin銆佸己鍒?act/wave 璺宠浆锛夈€侀厤缃?schema 鏍￠獙銆侀€氬叧鏃堕棿姒滐紙鍙︽敞鍐?Leaderboard锛夈€丄lertMenu 浜屾纭锛堝晢搴楃洰鍓嶇洿鎺ユ墸锛夈€丷ewardService.allPoolIds 杩斿洖涓嶅彲鍙樿鍥撅紙Minor锛夈€?- **闇€瑕?in-game QA**锛氶€氬叧/澶辫触 鈫?balance 澧?+ 閲嶅惎浠嶅湪锛沗/maggoteers shop` 鈫?涔拌В閿?鈫?涓嬪眬 3-pick 鑳?roll 鍒板凡瑙ｉ攣椤癸紱`/mgcop leaderboard create maggoteers_total` 鏀剧疆鎺掕姒滃疄浣撱€?
---

## 2026-07-04 鈥?绗簩杞?QA 淇钀藉湴锛? 浠诲姟 SDD + 鏈€缁堝瀹★紝QA#2锛?
### 鍋氫簡浠€涔?- 鎸?`docs/qa-findings-2026-07-04.md` 鐢?SDD 钀藉湴 4 浠诲姟锛堟瘡浠诲姟 implementer+reviewer锛屾渶缁?opus whole-branch 澶嶅 + M1/M2 淇锛夛細
  1. **甯搁┗鑽按姘镐箙鍖?+ 鎺掗櫎楗ラタ**锛氭柊澧?`GameplayTickListener`锛堝叏灞€ 1s 蹇冭烦锛宱nEnable 鍚?/ onDisable 鍋滐紝闈?per-game 闃查噸鍏ワ級锛涙瘡绉掑姣忎釜鍐掗櫓鐜╁ `EffectService.refreshPermanentPotions`锛堥噸鏂藉姞甯搁┗ ADD_POTION锛屽埛鏂?30s 涓嶈繃鏈燂級+ 缁存寔 food=20/saturation/exhaustion=0锛坄NATURAL_REGENERATION=false` 宸查樆鏂ケ椋熷洖琛€ 鈫?鎭掓弧涓嶅洖琛€锛夈€?  2. **娓呯悊鎭㈠澶у巺 profile**锛歚cleanupRun` 瀵规瘡鐜╁ `clear()` 鈫?`switchProfile(null)`锛堝厛娓呭悗鎭㈠锛岄伩鍏嶆竻鎺夊凡鎭㈠鐨勫ぇ鍘呰儗鍖咃級+ 澶嶄綅琛€/椋?鐏?鍧犺惤 鈫?闃蹭笅灞€绛夊緟甯︿笂涓€灞€鐗╁搧銆?  3. **绮掑瓙鑼冨洿鎻愮ず**锛氭柊澧?`util/ParticleEffects.playAreaRing`锛堟按骞崇矑瀛愮幆锛夛紝`EffectService` DAMAGE_AREA 鍒嗘敮璋冪敤锛堣寖鍥存墦鍑绘湁瑙嗚鍙嶉锛夈€?  4. **绀轰緥榄旀硶姝﹀櫒**锛歚TestBladeHandler`锛堝彸閿?`maggoteers:test_blade` 鈫?鑼冨洿鏂?绮掑瓙鐜?3s CD锛夛紝onEnable `registerHandler` 娉ㄥ唽鈥斺€旀紨绀?weapon 鍦板熀鍙敤锛坱ier-2 璺緞锛歬ind 涓嶅湪鏋氫妇鈫抜temIdOf 鍛戒腑鈫抙andler锛夈€?- 鏈€缁堝瀹★紙opus锛夛細0 Critical / 0 Important锛? Minor銆傝ˉ淇?M1锛圱estBlade 涓嶈浼ら槦鍙嬶紝鎺掗櫎鎵€鏈?Player锛? M2锛坱ier-2 handler 鍔?isAlive 闂ㄧ锛岄槻瑙傚療鑰呰Е鍙戯級銆?
### 鍐崇瓥涓庡師鍥?- **鑽按姘镐箙鍖栫敤 tick 鍒锋柊鑰岄潪 infinite duration**锛毬?0.3 璁捐鏈剰锛圤N_TICK_1S 瀹氭湡鍒锋柊锛夛紱infinite 鏃堕暱鍦?Paper 璺ㄦ浜′笉瀛樻椿銆佷笖鏀逛笉浜嗙瓑绾с€倀ick 姣忕閲嶆柦鍔犲父椹昏嵂姘达紙骞傜瓑锛夛紝绛夌骇鍙樻洿锛圲PGRADE_LEVEL锛夐殢鐪熸簮 params 鑷姩鍙嶆槧銆?- **switchProfile(null) belt-and-suspenders**锛氬瀹℃牳瀹?MGC `DefaultGameManager.endGame鈫抯olveGameEnd鈫抮emoveAllPlayer` 鍦?`onGameEnd` 涔嬪悗**宸茶嚜鍔?* `switchProfile(null)`锛涙垜浠湪 `cleanupRun` 鐨勬樉寮忚皟鐢ㄦ槸鍐椾綑浣嗘棤瀹崇殑淇濋櫓锛坄clear()`-before 椤哄簭鏃犺 MGC 鏄惁鎭㈠閮藉锛夈€?- **GameplayTick 鍏ㄥ眬鍗?task**锛堥潪 per-game 寮曠敤璁℃暟锛夛細閬垮厤 Plan 5 閭ｆ tickTask 琚?per-game cleanup 璇潃鐨?bug锛沗getAllGames()` 绌烘椂蹇冭烦绌鸿浆銆?
### 閬楃暀锛堟湭淇紝Minor锛?- **M3**锛歚cleanupRun` 鍏?`setHealth(20)` 鍚?`removeAll`锛堝墺 MAX_HEALTH modifier锛夛紱鑻ョ粨灞€鏃舵濂芥湁 MAX_HEALTH 璐?buff锛坢ax<20锛夛紝Paper clamp 鍚庣帺瀹跺彲鑳介潪婊¤绂诲満銆傜綍瑙侊紝闈炲洖褰掋€傚缓璁敼鐢?`PlayerExt.resetState()` 鎴栨妸 removeAll 鎻愬墠銆?- **M4**锛氬弻閲?`switchProfile(null)`锛堟垜鏂?+ MGC 鍚勪竴娆★級鍐椾綑寮傛钀界洏 + 鏃ュ織鍣煶锛涗繚鐣欎綔淇濋櫓锛屽彲鍘绘帀闈?MGC銆?- **璇嶇紑鑽按瀵规€棤鏁?*锛堜笂杞仐鐣欙級锛歚MobFactory` 浠嶄笉搴旂敤 `SpawnStep.potions()`锛沗toxic`/`vampiric` 绾嵂姘磋瘝缂€鍙樉绀哄悕銆備笅涓?plan 鍦?MobFactory 鍔犮€?
### 閮ㄧ讲
- jar 宸叉瀯寤?+ 澶嶅埗鍒?`/mnt/e/MCpaper/plugins/`銆?- 鈿狅笍 QA 鍓嶅垹鏃?`Maggoteers/` 鏁版嵁鐩綍鐨?yml锛坄saveResource(false)` 涓嶈鐩栵級銆傛棤鏂板 yml 瀛楁锛圦A2 鍏ㄦ槸浠ｇ爜 + 澶嶇敤宸叉湁閰嶇疆锛夛紝浣嗕笂杞紙QA#1锛夌殑閰嶇疆鍙樻洿锛? 鐐?STAT/STRENGTH/shop 鍒犻櫎绛夛級浠嶉渶娓呮棫鎵嶇敓鏁堛€?
---

## 2026-07-04 鈥?棣栬疆 QA 淇钀藉湴锛? 浠诲姟 SDD + 鏈€缁堝瀹★級

### 鍋氫簡浠€涔?- 鎸?`docs/qa-findings-2026-07-03.md` 鐢?SDD 钀藉湴 5 涓换鍔★紙姣忎换鍔?implementer+reviewer锛屾渶缁?opus whole-branch 澶嶅 + 涓€娆′慨澶嶏級锛?  1. **3 bug**锛歊estMenu 鏅€氭睜 tier 鏄犲皠锛坆oss鈫抯trong锛屄?.2锛夛紱鏈€缁?Boss 娓呮尝鐩存帴 `win()` 涓嶈繘浼戞暣锛沗cleanupRun` 閲嶇疆姣忎汉 GameMode鈫扴URVIVAL锛堥槻鍥炲ぇ鍘呬粛鏄瀵熻€咃級銆?  2. **澶嶆椿鐐瑰娲?*锛歚WaveScheduler.currentSpawnLocation(game)` + DeathStrategy 澶嶆椿鍒嗘敮浼犻€侊紙淇濈暀 2s 鏃犳晫锛夈€?  3. **supply_healing 鍙抽敭鐬洖**锛歚ItemKind.SUPPLY_HEALING` + router handler锛堢灛鍥?2銆佸彇娑堝師鐗堝悆銆佹秷鑰?銆佹弧琛€涓嶆秷鑰楋級锛沗RewardService.apply` 涓嶅啀鎶藉彇鏃跺洖琛€銆?  4. **鍟嗗簵 = 缁垮疂鐭崇墿鍝佸叆鍙?*锛堢敤鎴锋緞娓咃細涓嶆槸鎸夐挳锛夛細`ItemKind.SHOP_EMERALD`锛堝紑灞€鍙戙€佷笉娑堣€楋級鍙抽敭寮€ `ShopMenu`锛堣 `config.yml shop:` 娈碉紝鎸?normal/boss 甯佷拱鍥哄畾鍟嗗搧锛宲rice>1 寰幆鎵?澶辫触鍥為€€锛? `ShopConfig`銆?  5. **鎵╁厖娴嬭瘯鍐呭**锛堢函閰嶇疆锛夛細3 寮?points.yml 濉弧 9 鍒锋€偣锛沘ffixes.yml 4鈫?锛泈aves.yml 6鈫?1 strategy锛堢敤瓒?9 鐐广€佽瘝缂€鎸?strong/boss锛夛紱rewards.yml 瑕嗙洊鍏ㄩ儴 5 绉?Effect + ADD/UPGRADE_LEVEL/IGNORE + unique + 榄旀硶鐗╁搧濂栧姳銆?
### 鏈€缁堝瀹★紙opus锛夋姄鍒?+ 淇
- **I1**锛歚a2s_str` 鐢ㄤ簡 `INCREASE_DAMAGE`锛?.20.5 鏀瑰悕 `STRENGTH`锛?.21.7 `getByName` 杩斿洖 null 鈫?濂栧姳闈欓粯澶辨晥锛夆啋 鏀?`STRENGTH`锛沗RewardOption.parseParams` 鍔?null 璀﹀憡鏃ュ織銆?- 椤烘墜淇?Minor锛氭弧琛€涓嶆秷鑰楄嵂姘淬€丼hopConfig slot 瓒婄晫鏍￠獙銆丼hopMenu 鍐椾綑 `player.player()`銆乸oints.yml 鐐? 绉诲紑锛堝師涓?boss/spawn 閲嶅悎锛夈€?
### 閬楃暀 / 宸茬煡 gap
- **銆愬凡鐭?gap锛屾湭淇€戣瘝缂€鑽按瀵规€棤鏁?*锛歚MobFactory.spawn` 鍙 `hpMult/dmgMult/speedMult`锛?*浠庝笉搴旂敤 `SpawnStep.potions()`**锛圥lan 2/3 閬楃暀锛夈€傚鑷?*绾嵂姘磋瘝缂€**锛坄toxic` 鍑嬮浂銆乣vampiric` 鍐嶇敓锛夊彧鏄剧ず鍚嶅瓧銆佹棤瀹為檯鏁堟灉锛?*鏁板€艰瘝缂€**锛坅rmored/berserk/swift/tank/greedy/fortuned锛夋甯搞€侼eed#5 鎯虫祴鐨?璇嶇紑鑽按"鍥犳娴嬩笉鍒?鈫?涓嬩竴 plan 鍦?`MobFactory.spawn` 閲岀粰鎬姞 `step.potions()`锛堟敞鎰?`on: hit-player` 鐨勮嵂姘存槸缁欒鍑讳腑鐨勭帺瀹朵笂鐨勶紝闇€鍦ㄧ洃鍚櫒閲屽仛锛屼笉鏄粰鎬嚜宸卞枬锛夈€?- **Bug2 琛屼负宸紓**锛氭渶缁?Boss 鐭矾閫氬叧浼氳烦杩囪娉?`clearReward` 鍙戞斁 + `ON_WAVE_CLEAR` 瑙﹀彂鈥斺€斿綋鍓嶉厤缃棤 `ON_WAVE_CLEAR` 瑙﹀彂/鍒版湡鏁堟灉锛屼笖 `win()` 绔嬪嵆缁撴潫锛屽奖鍝嶅彲蹇界暐锛涜嫢浠ュ悗鏈?閫氬叧鍓嶆渶鍚庝竴娉㈡竻濂栧姳"闇€姹傦紝鎶婄煭璺Щ鍒板彂鏀?瑙﹀彂涔嬪悗銆?- **Minor 鏈慨**锛歵ier-1 璺敱 `setCancelled` 鍦?`isInGame` 鍒ゅ畾涔嬪墠锛堟父鎴忓鍙抽敭宸茬煡 kind 鐗╁搧浼氶潤榛?cancel 鍘熺増浜や簰锛岀綍鐜帮級锛沠inal-wave 璺宠繃 clearReward锛堣涓婏級銆?
### 閮ㄧ讲
- jar 宸叉瀯寤?+ 澶嶅埗鍒?`/mnt/e/MCpaper/plugins/`锛坄Maggoteers-0.1.0-SNAPSHOT.jar`锛夈€?- 鈿狅笍 **QA 鍓嶉渶鍒犳棫 `Maggoteers/` 鏁版嵁鐩綍閲岀殑鏃?yml**锛坄rewards.yml/points.yml/config.yml/items/maggoteers.yml/affixes.yml/waves.yml`锛夛紝`saveResource(false)` 涓嶈鐩栤€斺€斿惁鍒欐柊鍐呭锛圫TAT 濂栧姳銆? 鐐广€乻hop銆丼TRENGTH 淇锛変笉鐢熸晥銆?
---

## 2026-07-03 鈥?Plan 6 瀹炵幇锛氬鍔辨帴鏁堟灉寮曟搸 + 鐗╁搧/鑿滃崟鏀跺熬 + 鍙墿灞曟鍣ㄥ湴鍩猴紙SDD锛?
### 鍋氫簡浠€涔?- 鎸?`docs/plan/2026-07-03-plan6-rewards-items.md` 鐢?SDD 閫?Task 瀹炵幇锛? Task + 2 杞?critical/important 淇锛屽叏閮?review 閫氳繃锛夈€?- **淇簡鐢ㄦ埛 flag 鐨勪袱涓?bug**锛氬娲诲竵涓嶅彲鐢紙`ItemKind` 鍔?`REVIVE_COIN` + `ReviveMenu` 鐜╁澶村儚 GUI锛氱偣姝昏€呭娲?鐐规椿鑰?1銆佹墸甯併€佸け璐ラ€€杩橈級+ 鍙抽敭绌烘皵鎵撲笉寮€鑿滃崟锛坄ItemInteractRouter` `ignoreCancelled=false`锛夈€?- **濂栧姳鎺ユ晥鏋滃紩鎿?*锛歚RewardOption` reshape 鍒?搂12 schema锛坱rigger/effect/params/stack/icon/requires_unlock/unlock_cost/unique锛夛紱`rewards.yml` 鍔?STAT 閫夐」锛?浼ゅ/鍡滆/鎶楁€у崌绾?澶嶆椿+1/澶嶆椿+2锛夛紱`RewardService.apply` STAT鈫抈EffectService.apply`锛堝紩鎿庢湁浜嗙湡瀹炶緭鍏ワ級锛沗unique` 鍙鎬ц繃婊?+ `UPGRADE_LEVEL` 鍗囩骇銆?- **鍙墿灞曟鍣ㄥ湴鍩?*锛堢敤鎴烽噸鐐硅瘔姹?鍚庣画 agent 鑳藉湪鐜版湁鏁版嵁閾捐矾涓婂姞榄旀硶姝﹀櫒"锛夛細`ItemInteractRouter` 涓夊眰鍒嗗彂鈥斺€斿凡鐭?kind 鈫?`weapon_id` 娉ㄥ唽琛紙`registerHandler(weaponId, handler)` public API锛?*鏈潵姝﹀櫒鍦ㄦ鎸?handler锛岄浂璺敱鏍稿績鏀瑰姩**锛夆啋 `ON_INTERACT` 鍏滃簳锛堜粎褰撹鐜╁鎸?ON_INTERACT 鏁堟灉鎵嶈Е鍙戙€佸彧瑙﹀彂 clicker銆丆D 闂ㄧ锛夈€俙ItemService` 鍏ㄨ矾寰勬墦 `maggoteers:id` PDC锛坄itemIdOf`锛夛紝婵€娲讳簩/涓夊眰銆俙CooldownService`锛圖2锛屾寜 PDC-id 鏃堕棿鎴宠〃锛?*涓嶇敤 `setCooldown(Material)`**锛涘彲娉ㄥ叆鏃堕挓 + 4 渚嬪崟娴嬶級浣滀负 handler 鐨?CD 鍘熻銆?- **D1 鍑€鍖?*锛歚applyPotionPermanent` amp鈮?55 鎶€宸?+ `PurifyListener`锛圥OISON/WITHER damage-cancel fallback锛夈€?- 47 鍗曟祴鍏ㄧ豢銆?
### 鍐崇瓥涓庡師鍥狅紙鍚?review 鎶撳埌鐨勫叧閿己闄凤紝宸插叏閮ㄤ慨澶嶏級
- **鏁堟灉寮曟搸 vs 濂栧姳鐨勮緭鍏ヨ€﹀悎**锛歍ask 3 review 鎶撳埌涓や釜 Critical鈥斺€擿applyStat` 鐩存帴鏀瑰叡浜?`RewardOption.params()`锛堝浜?澶氭鎶戒細涓插懗锛夆啋 鏀?`EffectContext.copy()` 鍏嬮殕锛沗EffectStacker.merge` UPGRADE_LEVEL 鍗?level 涓嶅崌 amp锛坮esync 涓㈢瓑绾э級鈫?鍚屾 `same.params().amp++`銆?- **GRANT_REVIVE 涓嶈蛋寮曟搸**锛氭渶缁?review 鍙戠幇 `GRANT_REVIVE` 浣滀负甯搁┗鏁堟灉鏄?no-op锛坄applyDerived` 鏃犳 case锛夛紝涓旇嫢鍔犺繘鍘?resync 浼氶噸澶?granting銆傛敼涓?`RewardService.apply` 鐩村彂 `addReviveCount`锛堜竴娆℃€э紝涓嶈繃寮曟搸/涓嶈繘 PlayerState锛夈€?- **tier-3 ON_INTERACT 涓ゅ鍧?*锛?a) 鑷粠鍏ㄧ墿鍝佹墦 `maggoteers:id` 鍚庯紝tier-3 瀵规墍鏈夊鍔辩墿鍝?cancel 鍙抽敭锛堝紑绠辫鎷︼級鈫?鏀逛负"浠呭綋 clicker 鎸?ON_INTERACT 鏁堟灉鎵?cancel+fire"锛?b) `fireTrigger` 鏄叏灞€锛堟尝鍙婂叏鍛橈級鈫?鏂板 `fireTriggerPlayer(game, player, trigger)` 鍙Е鍙?clicker銆?- **UPGRADE_LEVEL level 鏉ユ簮**锛氬師鐢?`acquiredUnique` Set 璁℃暟锛圫et 澶╃劧灏侀《 1锛岀 3+ 娆℃娊 amp 閿欙級鈫?鏀硅鐪熸簮 `ps.effects()` 閲屽悓 id 鏁堟灉鐨?level銆?- **`Attribute` 鍦?Paper 1.21.7 涓嶆槸 Enum**锛圧egistry 绫伙級鈫?`RewardOption.parseParams` 鐢ㄤ笓鐢?`safeAttribute()` 鑰岄潪 `safeEnum`銆?- **`maggoteers:id` PDC 蹇呴』鎵?*锛氬惁鍒?`itemIdOf` 鎭?null銆佷簩/涓夊眰姝讳唬鐮侊紱`createItem`/`buildConfigured` 鍏ㄨ矾寰勮ˉ鎵撱€?
### 閬楃暀锛堝悗缁?Plan / 瀹炴祴锛?- **D1 鍑€鍖栨妧宸ч渶 in-game 瀹炴祴**锛?s-255 绾ф姷娑堝湪 Paper 1.21.7 鏄惁鎴愮珛鏈煡锛涗笉鎴愮珛鍒?`PurifyListener`锛坄immunity_<cause>` 姘镐箙鏁堟灉 id 绾﹀畾锛夊厹搴曘€傛垚绔嬪垯鍙垹 listener銆傞厤缃椂瑕佺粰鍑€鍖栨晥鏋滆捣 id `immunity_wither`/`immunity_poison` 鎵嶈兘瑙﹀彂 fallback锛堜袱璺緞鐙珛锛夈€?- **in-game 鎵嬫祴**锛氬彸閿┖姘?澶嶆椿甯?STAT 濂栧姳鐢熸晥/unique 杩囨护/UPGRADE 鍗囩骇鈥斺€旈渶閮ㄧ讲鍚庝汉宸ラ獙璇併€?- **Plan 7**锛歚requires_unlock` 鎸佷箙 unlocks锛堣处鎴疯揣甯?鍟嗗簵/瑙ｉ攣/鎺掕姒?缁撶畻锛夈€侀€氬叧鏃堕棿姒滐紙`RewardService.draw` 宸茬暀杩囨护閽╁瓙锛夈€?- **Plan 8**锛歚/maggoteers` 鍛戒护銆侀厤缃?schema 鏍￠獙銆佹寜 id 閰?CD 鐨?`item_cooldowns.yml`銆乣Map<String,Integer>` 鏇夸唬 `acquiredUnique` 鍙岀敤閫旇鏁帮紙鑻?UPGRADE_LEVEL 闇€姹傝秴杩?4 绾э級銆?- **鍔犻瓟娉曟鍣?= 鍐?handler + `registerHandler`**锛坱ier-2锛岄浂鏍稿績鏀瑰姩锛夛紱tier-3锛堟棤 handler 鐨?ON_INTERACT锛夊彲鐢ㄤ絾寤鸿浼樺厛 tier-2銆?
---

## 2026-07-03 鈥?Plan 5 瀹炵幇锛氭晥鏋滅郴缁熷紩鎿庯紙Trigger/Effect/鍒版湡/鍫嗗彔/resync锛孲DD锛?
### 鍋氫簡浠€涔?- 鎸?`docs/plan/2026-07-03-plan5-effects.md` 鐢?SDD 閫?Task 瀹炵幇锛? Task锛屾瘡 Task implementer+reviewer 澶嶆牳閫氳繃锛夈€?- 鏂板 `effect/` 鍖咃細`Trigger`(10)/`Effect`(5)/`Stack`(5) 鏋氫妇銆乣EffectKey<T>`+`EffectContext`(绫诲瀷鍖栧弬鏁?銆乣EffectKeys`(鏍囧噯閿?銆乣PlayerEffect`(鐢熷懡鍛ㄦ湡妯″瀷)銆乣EffectStacker`(merge/sweepExpiry 绾€昏緫)銆乣EffectService`(apply/resync/removeAll/fireTrigger/executeEffect)銆乣EffectListener`(鎴樻枟+ON_TICK_1S 蹇冭烦)銆?- `PlayerState` 鎵?`List<PlayerEffect>`锛涙帴鍏?fire-points锛歚WaveScheduler`(ON_WAVE_CLEAR/ON_ACT_ENTER)銆乣DeathStrategy`(ON_REVIVE+resync/ON_DEATH)銆乣MaggoteersGame.cleanupRun`(ON_GAME_END+removeAll+stopTick)銆乣onAllClassesChosen`(startTick)銆乣Plugin`(娉ㄥ唽鐩戝惉 + onDisable stopTick)銆?- 43 鍗曟祴鍏ㄧ豢锛堟柊澧?`EffectContextTest`/`EffectStackerTest`锛岀函閫昏緫瑕嗙洊鍫嗗彔/鍒版湡/merge锛夈€?
### 鍐崇瓥涓庡師鍥?- **寮曟搸涓庡鍔辫В鑰?*锛欳ursor 鐨?`RewardOption` 鏄畝鍖栫増锛堟棤 trigger/effect/params/stack锛夛紝`RewardService.apply` 鎶?STAT 纭紪鐮佹垚 +4 HP銆侾lan 5 鍙€犲紩鎿?+ 鎺ョ洃鍚櫒锛?*涓?*鍔?`RewardOption`/`RewardService`鈥斺€擿RewardOption` reshape + `RewardService` 鏀硅蛋 `EffectService.apply` 鐣?Plan 6锛堟晥鏋滃紩鎿庣殑鐪熷疄杈撳叆婧愶級銆傛晠鏈?Plan 寮曟搸鍏堥潬鍗曟祴 + 缂栬瘧楠岃瘉锛孭lan 6 鎺ラ€氬悗鎵嶆湁鐪熷疄鏁堟灉娴佸叆銆?- **D5 鍞竴 NamespacedKey**锛氬父椹?ADD_ATTRIBUTE 姣忓眰 `AttributeModifier` key = `id_level_鑷搴忓彿`锛?.21 鍚?key 閲嶅鎶涘紓甯革級銆?- **MGC API 淇**锛歜rief 鍘熷啓 `getGameManager().getRunningGames()`锛宩avap 鏍稿疄 MGC 1.0.5 `DefaultGameManager` 鏃犳娉曪紝鏀圭敤 `getAllGames()` + `instanceof MaggoteersGame` 杩囨护锛圤N_TICK_1S 蹇冭烦锛夈€?- **Attribute 鍛藉悕**锛歅aper 1.21.7 鐢ㄦ棤鍓嶇紑 `Attribute.MAX_HEALTH`锛堥潪 `GENERIC_*`锛夛紝涓?MobFactory 涓€鑷淬€?- **绾€昏緫/Bukkit 鍒嗗眰**锛歚EffectStacker`(merge/sweepExpiry) 涓?Bukkit 瑙ｈ€︺€佸叏鍗曟祴锛沗EffectService` 鐨?apply/resync/fireTrigger 鎿嶄綔 live Player 鈫?缂栬瘧 + 鎵嬫祴銆?
### 閬楃暀锛圥lan 6 澶勭悊锛?- `RewardOption` reshape + `RewardService.apply` 鏀硅蛋 `EffectService`锛堟晥鏋滃紩鎿庢墠鏈夎緭鍏ワ級銆?- D1 鍑€鍖栵紙`ADD_POTION` 0s-255 鎶垫秷 + damage-cancel fallback锛夈€丏2 鐗╁搧 CD锛坄cooldown_sec` 鎸?PDC-id 鏃堕棿鎴宠〃锛夈€乣ON_INTERACT` 瑙﹀彂銆?- 鍙抽敭绌烘皵鎵撲笉寮€鑿滃崟锛坄ItemInteractRouter.PlayerInteractEvent` 缂洪櫡锛? 澶嶆椿甯?GUI銆?- `EffectService.currentGame(p)` 鍙嶆煡鍙敼鏄惧紡浼?game锛沗removeAll` 宸茶ˉ娓呭父椹昏嵂姘达紙Task 3 review fix锛夈€?- in-game 鎵嬫祴锛堢洃鍚櫒涓嶅穿銆乫ireTrigger 鏃?NPE锛夐渶閮ㄧ讲鍚庝汉宸ラ獙璇併€?
---

## 2026-07-03 鈥?Plan 1鈥? 鍚堣瀹¤ + 鍒锋€灦鏋勭‘璁?+ 鐗╁搧浜や簰璺敱鎻愪氦

### 鍋氫簡浠€涔?- 瀵?GitHub 涓?`e1cc5ab`锛? origin锛夌殑 Plan 1鈥? 瀹炵幇鍋氬悎瑙勫璁★細涓変釜骞惰鍙瀹¤瀛愪唬鐞嗭紙Plan 2/3/4锛? 鎺у埗鍣ㄥ鏍?Plan 1锛屽鐓?`docs/plan/*`銆備复鏃?worktree 璺?`mvn test` 鈫?缁裤€?- 纭"姣忓紶鍥惧悇涓€浠?`points.yml`銆佸埛鏂扮偣 1鈥? 鏄犲皠鍦板浘鐩稿鍧愭爣"鐨勫埛鎬灦鏋?*宸茶鏀寔**锛堣涓嬶級銆?- 鎻愪氦鏈帹閫佺殑 WIP锛氱墿鍝?PDC 浜や簰璺敱 + 浼戞暣/鑱屼笟鑿滃崟鎺ョ嚎 + `ActSpawnHelper` + 鐗╁搧瀹氫箟銆?
### 瀹¤缁撹
- **Plan 1/2/4 瀹屽叏鍚堣**锛?*Plan 3 鍚堣**锛屼粎 `scaling.mob_hp` 鐢遍渶姹傛柟鎵嬪姩璋冨己锛坄[1.0,1.3,1.6,2.0]鈫抂1.0,2.3,3.6,5.0]`锛屾湁鎰忓钩琛★紝闈?bug锛夈€?- 涓€澶?鍋忕"瀹炰负**淇浜?Plan 2 鏂囨。鑷韩鐨勫潗鏍?bug**锛氭枃妗ｆ牱渚?`points.yml y:65` + 鐜荤拑鍦?`baseY-1` 浼氳鐜╁鍑虹敓鍦?y=129 鎮┖ 66 鏍硷紱瀹炵幇鏀逛负 `y:1` + 鐜荤拑鍦?`baseY`锛堝嚭鐢?y=65 韪╃幓鐠?y=64锛夈€傗啋 寰呭弽鍚戝悓姝ュ洖 Plan 2 鏂囨。銆?- `e1cc5ab` 杩樺す甯︿簡 Plan 5/6 鍐呭锛坄item/ItemService`銆乣menu/*`銆乣reward/*`銆乣ui/RunScoreboard`銆乣game/ClassSelectGate`锛夛紝鏈牬鍧?Plan 4 姝讳骸/澶嶆椿/澶辫触閾俱€?
### 鍒锋€灦鏋勭‘璁わ紙姣忓浘涓€浠?points.yml锛?- `MapRepository.load` 鎵弿 `maps/actN/*/` 涓?*鎵€鏈?*瀛愮洰褰曪紝鍑″惈 `points.yml` 鍗充负涓€寮犲浘锛堣嚜鍔ㄥ彂鐜帮紝鍔犲浘闆朵唬鐮侊級銆?- `RunPlanner.planAct` 姣忓眰绉嶅瓙鍖栨娊涓€寮犲浘锛屽埛鎬偣 id 缁?`map.points().point(id)` 鍙?*璇ュ浘鐩稿鍧愭爣**锛屽啀 `Coords.resolve(act_origins[act], 鐩稿)` 寰楃粷瀵瑰潗鏍囷紱`waves.yml` 鐨?`steps[].point` 鍙啓 id锛?1"鈥?9"/"boss"锛夈€?- **G3 鏍￠獙**锛氭煇娉㈠紩鐢ㄧ殑 id 涓嶅湪鎶戒腑鍥剧殑 `points.yml` 鈫?寮€灞€鍗虫姏閿欐寚鍒?strategy锛坒ail-fast锛夈€?- **濂戠害**锛氭瘡寮犲浘鐨?`points.yml` 蹇呴』瀹氫箟 `waves.yml` 浼氬紩鐢ㄧ殑鍏ㄩ儴 id锛堝綋鍓嶉粯璁ゅ彧鐢ㄤ簡 1/9/boss锛涜鐢?2鈥? 灏卞湪 points.yml 鍔犵偣 + 鍦?waves.yml 鍔犲紩鐢ㄥ嵆鍙級銆?- 褰撳墠 3 涓粯璁?`points.yml`锛坄ruined_keep/frozen_halls/obsidian_spire`锛屽潗鏍囩浉鍚屻€亂=1锛? 鏃?NBT 鏃剁殑鐜荤拑骞冲彴鍏滃簳銆傚姞鐪熷浘 = 鏂板缓 `maps/actN/<鏂板浘id>/{points.yml,4脳nbt[,special_waves.yml]}`銆?
### 閬楃暀
- Plan 2 鏂囨。鐨?`points.yml y:65` + 鐜荤拑 `baseY-1` 鏍蜂緥闇€鏀规垚 `y:1` + `baseY`锛堝厤寰楀悗浜洪噸韪╋級銆?- Plan 5/6锛堣彍鍗?濂栧姳/璁″垎鏉?鐗╁搧璺敱锛夊皻鏃犵嫭绔?plan 鏂囨。鍗冲凡钀藉湴锛涘悗缁ˉ鏂囨。鎴栧苟鍏?Plan 5/6 姝ｅ紡璁″垝銆?
---

## 2026-07-03 鈥?Plan 4 瀹炵幇锛圥layerState + 姝讳骸/澶嶆椿/澶辫触锛孋ursor 鎺ユ墜锛?
### 鍋氫簡浠€涔?- 鎸?`docs/plan/2026-07-03-plan4-playerstate-death-revive.md` 瀹屾垚 Task 1鈥?锛圱ask 4 闇€ in-game 鎵嬫祴锛夈€?- 鏂板锛歚PlayerState`/`PlayerStateManager`锛堝惈 `revivePlayer`/`addReviveCount` API 渚?Plan 6锛夈€乣GameOutcome`銆乣MaggoteersDeathStrategy`銆?- `MaggoteersGame`锛歚getPlayerDeathStrategy()` 娉ㄥ叆锛涘紑灞€ `switchProfile` + 鍐掗櫓妯″紡 + `PlayerStateManager.init(lives.default)`锛沗win()`/`fail()` 璁?outcome 鍚?`endGame`锛涚粨鏉熼挬瀛?`destroyAll`銆?- `WaveScheduler` 閫氬叧鍒嗘敮鏀硅皟 `((MaggoteersGame) game).win()`銆?- MGC API 宸?javap 鏍稿疄锛歚AbstractGame.getPlayerDeathStrategy()`銆乣AbstractPlayerDeathStrategy.onPlayerDeath(PlayerExt, PlayerDeathEvent)`銆乣PlayerExt.switchProfile(String)`銆?- `mvn test` 31 椤瑰叏缁匡紙鏂板 `PlayerStateTest` 4 椤癸級銆?
### 鍐崇瓥涓庡師鍥?- **DeathStrategy 鍙栨秷 PlayerDeathEvent**锛氫笉璧板師鐗堟浜″睆/閲嶇敓锛沗tryAutoRevive` 鍚?1鈫? 浠嶅娲伙紱鑰楀敖杞?SPECTATOR + `isAnyAlive` 鍒ゅけ璐ャ€?- **outcome 闃查噸鍏?*锛歚win()`/`fail()` 浠呭湪 `IN_PROGRESS` 鏃剁敓鏁堬紝閬垮厤閲嶅 endGame銆?- **switchProfile 淇濈暀**锛氭寜 plan + VampireSurvivor 瀹炶瘉鍦?onGameStart 鎵嬪姩鍒?profile锛涜嫢 in-game 瑙佽儗鍖呭弻娓呭啀绉婚櫎锛坉ev-log 寰呭疄娴嬭褰曪級銆?
### 閬楃暀
- Task 4 in-game锛氳嚜鍔ㄥ娲婚摼銆佸崟浜?澶氫汉澶辫触銆佽儨鍒?outcome=WIN锛涘娲诲竵 GUI 绔埌绔暀 Plan 6銆?- Plan 5 澶嶆椿璺緞琛?`EffectService.resync`锛汸lan 7 `onGameEnd` 鎸?outcome 缁撶畻璐у竵銆?
---

## 2026-07-03 鈥?Plan 3 瀹炵幇锛圧unPlanner + 缂╂斁 + 璇嶇紑锛孋ursor 鎺ユ墜锛?
### 鍋氫簡浠€涔?- 鎸?`docs/plan/2026-07-03-plan3-runplanner-scaling-affixes.md` 瀹屾垚鍏ㄩ儴 6 涓?Task銆?- 鏂板锛歚SeededRng`銆乣Affix`/`AffixService`/`affixes.yml`銆乣Compose`銆乣ScalingConfig`锛坈ount-roll D6锛夈€乣WaveDefinitions`/`MapLibrary`銆乣RunPlanner`銆乣RunConfig`锛沗SpawnStep.dropMult`锛沗MapEntry.specialWaves` + `special_waves.yml` 鏍蜂緥銆?- `MaggoteersGame` 鏀逛负 onGameStart 寮傛 `RunPlanner.plan(seed, playerCount)` + 浜屾灏辩华闂ㄩ棭锛沗WorldService.getSeed` 璁板綍涓栫晫 seed銆?- 鍒犻櫎 `SimplePlanner`锛堢敱 RunPlanner 鍙栦唬锛夈€俙mvn test` 27 椤瑰叏缁裤€?
### 鍐崇瓥涓庡師鍥?- **playerCount 寮€灞€閿佸畾 鈫?RunPlanner 鍦?onGameStart 寮傛**锛歰nGameInit 鏃朵汉鏁版湭瀹氾紝鏁呭湪 `startInWorld` 鍙?`getPlayers().size()` 鍚庡紓姝ヨ鍒掞紱seed = `WorldService.create` 鐨?world seed锛堝彲閲嶆斁锛夈€?- **WaveEngine/WaveScheduler 闆舵敼鍔?*锛歊unPlanner 浜у嚭鍚屼竴 `List<ActPlan>` 濂戠害锛岃鍒掍笌鎵ц瑙ｈ€︺€?- **ScalingConfigTest 姒傜巼娴嬭瘯**锛歱lan 鍘熸枃鐢?`new SeededRng(0..1999)` 鐨勯涓?`nextDouble()` 鍦?JDK 21 涓嬪潎 鈮?.5锛堝悓 seed 浣庝綅搴忓垪鐗规€э級锛屾敼涓哄崟 `SeededRng` 杩炵画鎶芥牱銆?
### 閬楃暀
- `dropMult` 宸茶绠楋紝Plan 6 `CurrencyService` 娑堣垂锛涜瘝缂€鑽按 Plan 5 钀藉湴銆?- 鍥哄畾 seed 璋冭瘯鍛戒护 鈫?Plan 8銆?- 闇€ in-game 楠岃瘉 Boss hp = coeff脳affix脳scaling锛坕ron_golem armored @1浜?鈮?320锛夈€?
---

### 鍋氫簡浠€涔?- Claude Code 棰濆害鐢ㄥ敖鍚庣敱 Cursor 鎸?`docs/plan/2026-07-03-plan2-maps-waves.md` 瀹屾垚 Plan 2 鍏ㄩ儴 8 涓?Task銆?- 鏂板锛歚waves.yml` + 涓夊紶榛樿 `points.yml`锛沗WavesConfig`/`MapRepository`/`StructurePaster`锛堢幓鐠冨钩鍙板厹搴曪級锛沗MobFactory`锛沗WaveEngine`/`WaveRuntime`/`WaveScheduler`/`MobDeathListener`锛沗SimplePlanner`/`Origins`锛沗MaggoteersGame` 寮€灞€闂幆锛堝缓涓栫晫 鈫?瑙勫垝 鈫?娉㈡锛夈€?- `mvn test` + `mvn package` 閫氳繃锛坄CoordsTest`/`WaveSpecTest`/`OriginsTest`锛夈€?
### 鍐崇瓥涓庡師鍥?- 涓ユ牸鎸?plan 鐓ф惉 VampireSurvivor 鐨?WaveManager/WorldManager 妯″紡锛汸lan 2 涓嶅仛缂╂斁/璇嶇紑/璐у竵鍙戞斁/clearReward 瀹炲彂/姝讳骸鍒ゅ畾锛堢暀 Plan 3鈥?锛夈€?- `WavesConfig.parseStrategy` 瀵?Bukkit `MapList` 鐨?wildcard 鍋氫簡 `num()` 杈呭姪锛岄伩鍏?`getOrDefault` 娉涘瀷缂栬瘧閿欒锛坧lan 鍘熸枃鍦?JDK 21 涓嬩笉閫氳繃锛夈€?
### 閬楃暀
- 闇€浜哄伐閮ㄧ讲 `target/Maggoteers-0.1.0-SNAPSHOT.jar` 鍒?`E:\MCpaper\plugins\` 鍋?in-game 楠岃瘉锛堣 plan Task 8 Step 3锛夈€?- 4 璞￠檺 NBT 浠嶇己锛寁1 闈?`map.fallback_platform` 鐜荤拑骞冲彴鍏滃簳銆?- 涓嬩竴姝ワ細**Plan 3**锛圧unPlanner 鏇挎崲 SimplePlanner锛夈€?
---

## 2026-07-03 鈥?缂栧啓 Plan 2 / 3 / 4 瀹炵幇璁″垝锛坵riting-plans锛?
### 鍋氫簡浠€涔?- 鎸?Plan 1 鐨勭害瀹氾紙`docs/plan/` 鍒嗛樁娈点€乀DD 姝ラ銆乧heckbox銆佺収鎼墠浠ｅ凡楠岃瘉瀹炵幇锛夌画鍐欎笁浠借鍒掞細
  - `2026-07-03-plan2-maps-waves.md`锛氱粨鏋勭矘璐?+ WaveEngine/Scheduler + 鍘熺増鎬?+ 涓夊眰闂幆 VICTORY銆?  - `2026-07-03-plan3-runplanner-scaling-affixes.md`锛歋eededRng + Affix + Compose + ScalingConfig(count-roll D6) + RunPlanner锛堢函鍑芥暟鍗曟祴锛夈€?  - `2026-07-03-plan4-playerstate-death-revive.md`锛歅layerState + MaggoteersDeathStrategy + outcome + 澶辫触鍒ゅ畾銆?- 澶嶆牳骞剁収鎼墠浠?`MCZJUvampireSurvivor` 宸查獙璇佸疄鐜帮細`WaveManager`锛圛dentityHashMap + UUID 鍙嶆煡 + 5-tick 瀹夊叏鎵弿锛夈€乣WorldManager`锛堢粨鏋勭矘璐达級銆乣ScalingConfig`銆乣SurvivorPlayerDeathStrategy`銆乣PlayerSession`銆乣SurvivorGame` 鐢熷懡鍛ㄦ湡銆?
### 鍐崇瓥涓庡師鍥?- **Plan 2鈫擯lan 3 绋冲畾濂戠害**锛氬畾涔?`ActPlan{mapId,playerSpawn,waves}` / `WaveSpec{steps,repeat,clearReward}` / `SpawnStep{...}`锛堝凡瑙ｆ瀽缁濆鍧愭爣 + 鏈€缁堝€嶇巼锛変负璺?Plan 涓嶅彉閲忋€侾lan 2 鐢ㄥ悓姝?`SimplePlanner`锛坈oeff-only銆乧ount 鍥哄畾锛変骇鍑?`List<ActPlan>`锛汸lan 3 鐨勫紓姝ョ瀛愬寲 `RunPlanner` **鏇挎崲** `SimplePlanner`锛?*WaveEngine/WaveScheduler 闆舵敼鍔?*鈥斺€旀妸"瑙勫垝"涓?鎵ц"瑙ｈ€︼紝鏈€灏忓寲杩斿伐銆?- **RunPlanner 鏀?onGameStart 寮傛锛屼笉鏀?onGameInit**锛毬?.3 瀛楅潰璇?onGameInit 鍚姩 RunPlanner锛屼絾 playerCount 鏄?寮€灞€蹇収閿佸畾"锛埪?/搂1.1锛夛紝onGameInit 鏃剁帺瀹跺彲鑳借繕鍦ㄩ泦缁擄紝count 鏈畾銆傛晠鏀惧湪 `onGameStart` 鐨勫氨缁棬闂╁唴寮傛璺戯紙鍙?`getPlayers().size()` 閿佸畾蹇収锛夛紝涓?G4"寤轰笘鐣屽敮涓€鍦?onGameStart 闂ㄩ棭涓荤嚎绋?涓€鑷淬€倃orld seed 鐢?`WorldService.create` 鐢熸垚骞?`getSeed` 鏆撮湶缁?RunPlanner锛堜笘鐣屽悕 hex = seed hex锛屽彲閲嶆斁锛夈€?- **绾嚱鏁板彲娴?*锛歚WaveDefinitions`/`MapLibrary`/`RunConfig` 绾暟鎹?record + `RunPlanner.plan(seed,playerCount,defs,maps,scaling,affixes,cfg)` 鍏ㄦ敞鍏ャ€佷笉纰?Bukkit锛沗AffixService.forTesting`/`ScalingConfig.forTesting` 鍖呯骇宸ュ巶缁曡繃鍗曚緥鈥斺€擯lan 3 鍏ㄥ鍗曟祴锛堢‘瀹氭€?G3/count-roll/涓撳睘娉㈡骞跺叆锛夋棤闇€寮€鏈嶃€?- **SpawnStep 瀛楁婕旇繘**锛歅lan 2 瀹氫箟 9 瀛楁锛汸lan 3 鍔?`dropMult`锛坈oeff脳affix脳scaling 鐨勬帀钀藉€嶇巼锛孭lan 6 CurrencyService 娑堣垂锛? 濉?`affixes/potions`銆俙dropMult` 钀藉湴瀛楁鍦?Plan 3锛岄伩鍏?Plan 6 鍐嶆敼妯″瀷銆?- **姝讳骸绛栫暐鎺ュ叆鐐瑰凡婧愮爜鏍稿疄**锛歚AbstractGame.getPlayerDeathStrategy()` override 杩斿洖 `new XxxDeathStrategy(this)`锛圴ampireSurvivor `SurvivorGame:75` 瀹炶瘉锛夛紱绛栫暐 `onPlayerDeath(PlayerExt, PlayerDeathEvent)` 鍙栨秷浜嬩欢 + reviveCount 閫昏緫銆俙switchProfile(getId())` 鍦?onGameStart 鍒囧共鍑€娓告垙 profile锛坄SurvivorGame:147` 瀹炶瘉锛夆€斺€擯lan 1鈥? 缂哄け锛孭lan 4 琛ヤ笂銆?- **outcome 鏍囧織锛圖4锛?*锛歚GameOutcome{IN_PROGRESS,WIN,FAIL}` 钀藉湪 `MaggoteersGame` 瀹炰緥锛沗WaveScheduler` 鑳滃埄璋?`win()`銆乣DeathStrategy` 澶辫触璋?`fail()`锛岄兘 `setOutcome + endGame`銆侾lan 7 鐨?`onGameEnd` 鎹鍒嗘敮缁撶畻銆?
### 閬楃暀 / 寰呭疄鐜版湡鏍稿疄
- Plan 2 鍦板浘 4 璞￠檺 NBT 闇€杩愮淮鐢ㄧ粨鏋勬柟鍧楀鍑猴紱v1 鐢ㄧ幓鐠冨钩鍙板厹搴曪紙`map.fallback_platform`锛夈€?- Plan 4 `switchProfile` 鏄惁涓?MGC 鑷姩鍒囨崲閲嶅鈥斺€斿疄娴嬪悗瀹氾紙鑻ュ紑灞€鑳屽寘琚竻涓ゆ鍒欑Щ闄ゆ墜鍔ㄨ皟鐢級銆?- ItemCreator jitpack 鐗堟湰鍙枫€佸噣鍖栨妧宸э紙D1锛変粛寰呭疄鐜版湡鏍稿疄/瀹炴祴銆?- 澶嶆椿鏃?`EffectService.resync` 琛ュ洖鑽按鈥斺€擯lan 5 鍦ㄥ娲昏矾寰勫姞 hook銆?- 鍏ㄩ儴涓変唤 Plan 渚濊禆鏈満瑁?JDK21+Maven 鎵嶈兘璺?`mvn`/閮ㄧ讲锛圕laude 渚у彧鍐欐枃浠讹級銆?
---

## 2026-07-02 鈥?璁捐琛ュ厖锛氬紑灞€鑱屼笟閫夋嫨

### 鍋氫簡浠€涔?- 闇€姹傛柟琛ュ厖锛氱帺瀹惰繘鍥炬棤瑁呭锛岄渶**寮€灞€鑱屼笟閫夋嫨 + 鍏ㄥ憳鍒濆瑁呭**銆傚凡鍐欏叆 CLAUDE.md 搂9.4 / 搂2.3 / 搂4.1 / 搂7.6銆乻pec 搂8銆?
### 鍐崇瓥
- **澶嶇敤濂栧姳姹?+ 瑙ｉ攣**锛氳亴涓氭睜 = `reward_pools.class`锛坄cost: 0` 鍏嶈垂锛夛紝option 鍗虫爣鍑?`RewardOption`锛圫TAT/WEAPON/SUPPLY锛屾敮鎸?`requires_unlock`/`unique`锛夈€傚紑灞€姣忎汉 3 閫?1锛屽彲瑙佹€ц繃婊ゅ師鏍峰鐢ㄢ€斺€旈浂鏂版蹇点€?- **鍒濆瑁呭**锛歚config.yml` `initial_equipment`锛圛temCreator id 鍒楄〃锛夛紝寮€灞€鍙戝叏鍛橈紝璧?`ItemService` + `giveItem(ItemStack)`锛堥伒瀹?G2锛夈€?- **寮€灞€闂ㄩ棭**锛歚onGameStart` 闂ㄩ棭灏辩华鍚?鈫?鍙戝垵濮嬭澶?鈫?寮€ `ClassSelectMenu` 鈫?鍏ㄥ憳閫夊畬锛堝€掕鏃跺厹搴曪級鈫?鍚姩 `WaveScheduler`銆傚€熷墠浠?VampireSurvivor `ClassSelectMenu` / `onAllClassesChosen` 宸查獙璇佸璺€?- 鍔犺亴涓?= 閰嶇疆鍔?option锛?*闆朵唬鐮?*銆?
---

## 2026-07-02 鈥?绗笁杞帴鍏ュ绾﹀鏍革紙搂G 鏁存敼锛?
### 鍋氫簡浠€涔?- 澶勭悊鏍稿娓呭崟绗笁杞紙瀵圭収 1.0.5 clone + 鏁存敼鍚庡叏鏂囷級銆傜粨璁猴細**鏃?Blocker锛屽彲杩?writing-plans**銆傛暣鏀?搂G1鈥揋4銆?
### 鍐崇瓥涓庡師鍥狅紙婧愮爜瀹炶瘉 G1/G2锛?- **G1 鎴块棿瀹炰緥**锛歚registerGame` 鈫?`loadGameRoom`锛坄DefaultGameManager:43`锛夛紝鎴块棿 JSON 鍦?`plugins/MCZJUGameCore/rooms/maggoteers/*.json`锛?*鏃?READY 鎴块棿鍒?`/mgc join` 澶辫触**銆傗啋 onEnable 鍏堥噴鏀鹃粯璁?`default.json` 鍐?registerGame锛堟垨杩愮淮 `/mgcop room create maggoteers default`锛夈€俽oom 鏄皟搴﹀３銆佸彲澶嶇敤锛涙瘡灞€浠嶈嚜寤鸿櫄绌轰笘鐣屻€?- **G2 giveItem 璺敱**锛歚PlayerExt.giveItem(String)` 璧?MGC ItemManager锛?*闈?* ItemCreator锛夛紱ItemCreator 鐗╁搧蹇呴』 `ItemService.createItem(id)` + `giveItem(ItemStack)`锛坄PlayerExt:111`锛夈€?- **G3 鍒锋€偣 schema**锛歱oints.yml 绀轰緥琛?`boss`锛汻unPlanner 鏍￠獙 `steps[].point` 缂栧彿鍦ㄨ鍥?points.yml 瀛樺湪銆?- **G4 寤轰笘鐣屽綊灞?*锛氬敮涓€鍦?`onGameStart` 灏辩华闂ㄩ棭鍐咃紙涓荤嚎绋?`createWorld` + 绮樿创 Act1锛夛紱`onGameInit` 鍙紓姝?RunPlanner + NBT 棰勮銆?*涓嶅缓涓栫晫**鈥斺€旈伩鍏嶅弻瑙﹀彂銆?- 鏂板 CLAUDE.md 搂16.1 棣栨祴閮ㄧ讲娓呭崟锛圡GC 1.0.4鈫?.0.5銆乣rooms/maggoteers/default`銆佽祫浜с€両temCreator锛夈€?
### 閬楃暀 / 杩?plan
- 搂G 閮ㄧ讲椤癸紙MGC 鍗囩骇銆乺oom 鍒涘缓锛変綔涓?plan 闃舵 0銆?- ItemCreator 鐗堟湰鍙枫€佸噣鍖栨妧宸э紙D1锛夊疄鐜版湡鏍稿疄/瀹炴祴銆?
---

## 2026-07-02 鈥?绗簩杞帴鍏ュ绾﹀鏍革紙搂E 鏁存敼锛?
### 鍋氫簡浠€涔?- 澶勭悊鏍稿娓呭崟绗簩杞紙瀵圭収 GitHub 1.0.5 婧愮爜 + 娴嬭瘯鏈?1.0.4 jar锛夛紝鏁存敼 搂E1鈥揈8 鏂囨。閬楁紡銆?
### 鍐崇瓥涓庡師鍥狅紙婧愮爜瀹炶瘉锛?- **E1 鎸佷箙鍖栬矾寰?*锛歚JsonPlayerData.getFilePath()` = `<MGC鏁版嵁鐩綍>/player_data/<gameId>/<uuid>.json` 鈫?瀹為檯 `plugins/MCZJUGameCore/player_data/maggoteers/<uuid>.json`锛?*涓嶅湪鏈彃浠剁洰褰?*锛夈€俙dev-advanced.md` 鐨?`player/...` 宸茶繃鏃躲€?- **E2 钀界洏鍛ㄦ湡**锛歚MCZJUGameCore.java:73 startAutoSave(20*60*30L)` = **30 鍒嗛挓**锛堥潪 5 鍒嗛挓锛? 閫€鍑?`savePlayerDataAsync` + 鍏虫湇 `saveAllPlayerData`銆?- **E3 鐗堟湰**锛氭祴璇曟湇 jar=**1.0.4 宸插惈** PlayerData API锛堥潪"缂?API 缂栬瘧涓嶈繃"锛夛紱璁捐鍩虹嚎 1.0.5锛屽缓璁崌绾у榻愩€傛湰鍦?clone=1.0.0 杩囨椂锛堟棤 PlayerData銆丮enu API 涔熶笉鍚岋級銆?- **E4 Menu锛?.0.5锛?*锛氬瓙绫?`public XxxMenu(Player, Object...){ super(player,args); }` + 閲嶅啓 `getTitle/getRows/getPermission/setup`銆?- **E5 鎺掕姒?*锛歚getLeaderboardManager().registerLeaderboard(id, Class)` + `PlayerDataLeaderboard` 鍥涙娊璞℃柟娉曪紙`getTitle/getSubtitle/getPlayerDataClass/getFieldName`锛夈€?- **E6/E7/E8**锛毬?.4 浜ゅ弶寮曠敤锛堚啋搂13.1/搂13.5锛夈€乻pec 涓?CLAUDE.md 鍚屾銆丷unPlan 鍘婚櫎 `World` 寮曠敤锛堝紓姝ョ嚎绋嬩笉鎸佹湁 World锛屼富绾跨▼缁戝畾鍐嶆瀯閫?Location锛夆€斺€斿潎宸叉敼銆?
### 閬楃暀
- ItemCreator jitpack 鐗堟湰鍙枫€佸噣鍖栨妧宸э紙D1锛夊疄鐜版湡鏍稿疄/瀹炴祴銆?
---

## 2026-07-02 鈥?鎺ュ叆濂戠害澶嶆牳涓庢暣鏀癸紙review锛?
### 鍋氫簡浠€涔?- 澶勭悊 `docs/review/2026-07-02-鎺ュ叆濂戠害鏍稿娓呭崟.md`锛堜竴浠介€愭潯瀵圭収鐪熷疄婧愮爜鐨勮嚜妫€娓呭崟锛夈€?- 閲嶆柊鏍稿 GitHub 鏈€鏂扮増 MGC 婧愮爜锛涢渶姹傛柟瑁佸畾**浠?GitHub 鏈€鏂扮増涓哄噯**锛堟祴璇曟湇鏃х増杩囨椂锛夈€?
### 鍐崇瓥涓庡師鍥?- 娓呭崟 **搂A / 搂B1 / 搂B2 / 搂B5 璇佷吉**鈥斺€斿畠浠熀浜庤繃鏃剁殑鏈湴婧愮爜锛堟棤 PlayerData 绯荤粺锛夛紱GitHub 鏈€鏂扮増鏈夊畬鏁?`JsonPlayerData`/`getData`/`resetState`/`PlayerDataLeaderboard`锛屽墠浠?`build.gradle` 涔熷疄璇佷簡 jitpack groupId `com.github.mczju-ops`銆侰LAUDE.md 杩欏嚑澶勫師姝ｇ‘锛屼笉鏁存敼銆?- 娓呭崟 **搂B3 / 搂B4 鎴愮珛**锛堢増鏈棤鍏崇殑璁捐鐤忔紡锛夛細`createWorld()` 蹇呴』涓荤嚎绋嬶紱`onGameStart` 杩斿洖 void 涓嶈兘闃诲 鈫?寮曞叆"灏辩华闂ㄩ棭"锛坄runTaskTimer` 杞锛夈€?- 娓呭崟 **搂D1鈥揇8 鍏ㄩ儴鎴愮珛**锛屽凡浣滀负瑙勫垯/椋庨櫓鍐欏叆 CLAUDE.md锛欳D 璧?PDC 鏃堕棿鎴宠〃锛堜笉鐢?`setCooldown(Material)`锛夈€? 閫?1 涓嶈冻鐏版樉涓嶈ˉ鍑戙€佽儨璐?`outcome` 鏍囧織銆佸睘鎬у灞傚敮涓€ `NamespacedKey`銆佽ˉ鎬蛋绉嶅瓙 RNG銆佽Е鍙戣鍔ㄩ粯璁?`IGNORE`銆佸噣鍖?0s 255 绾?鎶€宸у緟瀹炴祴銆?- **鏂板瑕佸**锛氭祴璇曟湇蹇呴』鍗囩骇鍒?GitHub 鏈€鏂扮増 MGC锛屽惁鍒欐寔涔呭寲鏁寸珷澶辨晥锛堝啓鍏?CLAUDE.md 搂1.2 鐗堟湰鍩虹嚎 + 搂18锛夈€?
### 閬楃暀
- ItemCreator jitpack 鐗堟湰鍙峰緟鏍稿疄銆?- 鍑€鍖栨妧宸у疄鐜版湡瀹炴祴銆?
---

## 2026-07-02 鈥?绔嬮」涓庤璁″畾绋匡紙brainstorm 闃舵锛?
### 鍋氫簡浠€涔?- 涓庨渶姹傛柟閫愭纭浜?The Maggoteers锛堝崼鎴嶅崗璁級鐨勫畬鏁磋璁★紝浜у嚭 `CLAUDE.md`锛堥」鐩湥缁忥級涓?`docs/spec/2026-07-02-maggoteers-design.md`锛堣璁″彊浜嬶級銆?- 闃呰骞跺€熼壌浜嗕笁涓浉鍏抽」鐩細
  - `MCZJUGameCore`锛堜緷璧栨鏋讹級鈥斺€旂‘璁ゆ帴鍏ュ绾︼紙`AbstractGame`/`JsonGameRoom`/`JsonPlayerData`/`PlayerExt`/鎺掕姒?Menu锛夈€?  - `MCZJUItemCreator`锛堢墿鍝?API锛夆€斺€擿ServicesManager` 鍙?API锛沗createItem`/`parseYamlToItems`銆?  - `MCZJUvampireSurvivor`锛堝墠浠ｅ悓娆惧師鍨嬶級鈥斺€旂洿鎺ュ€熼壌**宸查獙璇?*鐨勪笘鐣?铏氱┖/缁撴瀯绮樿创/娉㈡寮曟搸/姝讳骸绛栫暐/缂╂斁銆?  - `MCZJUMagicItems`锛堟晥鏋滆寖寮忥級鈥斺€斿€熼壌"瑙﹀彂鈫掑垎鍙戔啋鏁堟灉"PDC 鑼冨紡銆?
### 鍏抽敭鍐崇瓥涓庡師鍥?- **涓嶅紩鍏?WorldEdit/MythicMobs/InfernalMobs**锛氭牳蹇冭瘔姹傛槸"绾厤缃€佷究浜庣鐞?锛岀涓夋柟鎬墿鎻掍欢 API 涓嶅彲鎺с€傜簿鑻辨€?鑷疄鐜伴厤缃瘝缂€灞傘€?- **璐︽埛璐у竵鑷缓**锛氬彂鐜?MGC 鐨?`ScoreManager`/`HistoryScoreManager` 鏄?*绌哄３**锛屾棤鍙敤绉垎绯荤粺銆?- **涓栫晫=姣忓眬鏂板缓铏氱┖涓栫晫**锛氬鎴块棿澶╃劧骞惰锛涚粨鏋勭矘璐寸敤 Paper `Structure` API锛堜笉渚濊禆 WE锛夛紝鏂规硶宸茶 VampireSurvivor 楠岃瘉銆?- **娉㈡鏃跺簭=鍗曚竴鍙殏鍋滅姸鎬佹満**锛氫紤鏁存湡瑕佹殏鍋溿€佽烦杩?澶辫触瑕佺粺涓€鍙栨秷锛岄摼寮?task 鍋氫笉鍒般€?- **鏁堟灉鐢熷懡鍛ㄦ湡=浜嬩欢鍒版湡锛岄潪鎸傞挓璁℃椂**锛堥渶姹傛柟鍏抽敭淇锛夛細闄愭椂閬撳叿闄愮殑鏄?娉㈡/灞傜骇/涓嬫鏀诲嚮/涓嬫澶嶆椿"绛変簨浠讹紝涓旀晥鏋滃繀椤?*璺ㄦ浜″娲诲瓨娲?*鈥斺€斿師鐗堣嵂姘?infinite 鏃堕暱鍋氫笉鍒般€傛晠 PlayerState 涓虹湡鐩告簮銆佸娲诲悗 resync锛屽垹闄ゅ墠浠?`BuffInstance` 瀹炴椂璁℃椂銆?- **榄旀硶姝﹀櫒 v1 涓嶅仛妗嗘灦**锛氶渶姹傛柟婢勬竻鈥斺€擨temCreator 鍑哄甫 PDC 鐗╁搧銆佹湰鎻掍欢鍐欒瘑鍒?瑙﹀彂绫诲嵆鍙紝鏃犻渶閫氱敤鎺ュ彛銆傜暀浣?PDC 璺敱"鏂囨。鍖栨墿灞曡矾寰勩€?- **鎺掕姒滅Н鍒?= `totalEarned`**锛氬厤鍗曠嫭璁″垎绯荤粺銆?- **Boss 姹犵矘婊?*锛? 鏈€杩戝嚮鏉€ Boss 鎵€鍦ㄥ眰锛屾潃鎺夋湰灞?Boss 鎵嶆洿鏂帮紙闇€姹傛柟绮惧寲锛夈€?- **缂╂斁寮€灞€閿佸畾 + 涓嶅厑璁镐腑閫斿姞鍏?*锛氱畝鍖栭鐢熸垚涓庤皟璇曘€?
### 閬楃暀 / 寰呭疄鐜版湡鏍稿疄
- ItemCreator 鐨?jitpack 鐗堟湰鍙枫€?- `parseYamlToItems` 涓?ItemCreator 鍘?`items/` 鍔犺浇璺緞鐨勭壒鎬х瓑浠锋€э紙鐢ㄤ竴涓鏉傛鍣ㄩ獙璇侊級銆?- 灏氭湭鍐欎换浣曚唬鐮侊紱涓嬩竴姝ヨ繘鍏?**writing-plans**锛屼骇鍑?`docs/plan/` 鍒嗛樁娈靛疄鐜拌鍒掋€?
---

<!-- 鏂版潯鐩寜姝ゆ牸寮忕户缁拷鍔犲湪鏈€涓婃柟锛堟棩鏈熷€掑簭锛?-->
