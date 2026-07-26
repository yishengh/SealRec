from pathlib import Path

root = Path("app/src/main/res")

zh_cn = (root / "values-zh-rCN" / "strings.xml").read_text(encoding="utf-8")
(root / "values-zh").mkdir(exist_ok=True)
(root / "values-zh" / "strings.xml").write_text(zh_cn, encoding="utf-8", newline="\n")

zh_tw = r'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">SealRec</string>
    <string name="notif_channel">密封錄音</string>
    <string name="notif_recording">正在密封錄音</string>
    <string name="notif_paused">錄音已暫停</string>
    <string name="action_pause">暫停</string>
    <string name="action_resume">繼續</string>
    <string name="action_stop">停止並密封</string>
    <string name="tab_record">錄音</string>
    <string name="tab_library">庫</string>
    <string name="tab_verify">鑑定</string>
    <string name="device_time_disclaimer">時間戳為裝置本地時間，可能受系統時間影響，非可信 TSA。</string>
    <string name="status_intact">綠標：絕對未修改</string>
    <string name="status_tampered">紅標：音訊位元組被竄改</string>
    <string name="status_bad_sig">黃標：簽名偽造或金鑰不符</string>
    <string name="status_not_seal">灰標：非 SealRec 密封檔案</string>
    <string name="incomplete_title">發現未完成的錄音</string>
    <string name="incomplete_repair">修復並密封</string>
    <string name="incomplete_discard">丟棄</string>
    <string name="permission_mic">需要麥克風權限以進行密封錄音</string>
    <string name="permission_notif">需要通知權限以顯示錄音前景服務</string>
    <string name="export_ok">已匯出到 Music/SealRec</string>
    <string name="export_fail">匯出失敗</string>
    <string name="offline_badge">完全離線 · 零網路權限</string>
    <string name="brand_subtitle">硬體金鑰密封 · 本機驗真</string>
    <string name="library_title">錄音庫</string>
    <string name="library_subtitle">本地密封檔案 · 可隨時離線鑑定</string>
    <string name="library_empty">暫無錄音</string>
    <string name="verify_title">防竄改鑑定</string>
    <string name="verify_subtitle">選擇任意 WAV，離線解析 seal chunk 並驗簽。</string>
    <string name="verify_pick">選擇檔案鑑定</string>
    <string name="verify_clear">清除報告</string>
    <string name="msg_sealed">已密封儲存</string>
    <string name="msg_repaired">未完成錄音已修復並密封</string>
    <string name="state_idle">就緒</string>
    <string name="state_recording">密封中 · 雜湊引擎運作中</string>
    <string name="state_paused">已暫停</string>
    <string name="state_finalizing">正在簽名打包…</string>
    <string name="action_verify">鑑定</string>
    <string name="key_fingerprint">金鑰指紋 %1$s</string>
    <string name="last_verify">最近鑑定：%1$s</string>
    <string name="rename_title">重新命名錄音</string>
    <string name="rename_label">檔案名稱</string>
    <string name="rename_confirm">儲存</string>
    <string name="rename_cancel">取消</string>
    <string name="rename_ok">已重新命名</string>
    <string name="rename_fail">重新命名失敗（名稱無效或已存在）</string>
    <string name="play_fail">播放失敗：%1$s</string>
    <string name="play_busy_recording">密封錄音進行中，無法播放</string>
    <string name="record_too_short">錄音過短或為空，已丟棄</string>
    <string name="record_failed">錄音失敗</string>
    <string name="export_verify_ok">已匯出 WAV 與核驗 JSON</string>
    <string name="tab_settings">設定</string>
    <string name="settings_language">語言</string>
    <string name="settings_night">夜間模式</string>
    <string name="settings_quality">錄音品質</string>
    <string name="settings_notif_sounds">錄音時允許通知提示音</string>
    <string name="settings_trash">回收桶（%1$d）</string>
    <string name="settings_about">關於 SealRec</string>
    <string name="lang_system">跟隨系統</string>
    <string name="lang_en">English</string>
    <string name="lang_zh">中文</string>
    <string name="night_system">跟隨系統</string>
    <string name="night_light">淺色</string>
    <string name="night_dark">深色</string>
    <string name="quality_16k">16 kHz 語音</string>
    <string name="quality_44k">44.1 kHz CD</string>
    <string name="quality_48k">48 kHz 錄音室</string>
    <string name="trash_title">回收桶</string>
    <string name="trash_empty">回收桶是空的</string>
    <string name="trash_restore">還原</string>
    <string name="trash_purge">永久刪除</string>
    <string name="trash_empty_all">清空回收桶</string>
    <string name="about_title">關於 SealRec</string>
    <string name="about_proves_title">SealRec 能證明什麼</string>
    <string name="about_proves_body">錄音過程中逐樣本計算雜湊，並使用永不離開本裝置的硬體金鑰簽名。鑑定結果為綠標即證明：音訊位元組與麥克風採集時完全一致，且簽名由本裝置金鑰產生。</string>
    <string name="about_not_title">SealRec 不能證明什麼</string>
    <string name="about_not_body">SealRec 無法證明說話人身分、錄音地點，也無法證明聲音本身不是擺拍或經揚聲器重放。它只證明數位檔案自密封以來未被修改。</string>
    <string name="about_time_title">關於裝置時間</string>
    <string name="about_time_body">密封中嵌入的時間戳來自裝置本地時鐘，而非可信時間戳機構（TSA）。若系統時間不準或被修改，密封時間也會隨之偏差。請將其視為參考資訊，而非錄音時間的證明。</string>
</resources>
'''
(root / "values-zh-rTW").mkdir(exist_ok=True)
(root / "values-zh-rTW" / "strings.xml").write_text(zh_tw, encoding="utf-8", newline="\n")


def escape(v: str) -> str:
    return (
        v.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", r"\'")
    )


def write_locale(folder: str, data: dict) -> None:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<resources>",
        '    <string name="app_name">SealRec</string>',
    ]
    for k, v in data.items():
        lines.append(f'    <string name="{k}">{escape(v)}</string>')
    lines.append("</resources>")
    lines.append("")
    p = root / folder
    p.mkdir(exist_ok=True)
    (p / "strings.xml").write_text("\n".join(lines), encoding="utf-8", newline="\n")
    print("wrote", folder)


ja = {
    "notif_channel": "封印録音",
    "notif_recording": "封印録音中",
    "notif_paused": "録音一時停止",
    "action_pause": "一時停止",
    "action_resume": "再開",
    "action_stop": "停止して封印",
    "tab_record": "録音",
    "tab_library": "ライブラリ",
    "tab_verify": "検証",
    "device_time_disclaimer": "タイムスタンプは端末のローカル時刻です（信頼できる TSA ではありません）。",
    "status_intact": "緑：改ざんなし",
    "status_tampered": "赤：音声バイト改ざん",
    "status_bad_sig": "黄：署名不正 / 鍵不一致",
    "status_not_seal": "灰：SealRec ファイルではない",
    "incomplete_title": "未完了の録音が見つかりました",
    "incomplete_repair": "修復して封印",
    "incomplete_discard": "破棄",
    "permission_mic": "封印録音にはマイク権限が必要です",
    "permission_notif": "フォアグラウンドサービス通知の権限が必要です",
    "export_ok": "Music/SealRec に書き出しました",
    "export_fail": "書き出しに失敗しました",
    "offline_badge": "完全オフライン · ネットワーク権限なし",
    "brand_subtitle": "ハードウェアキー封印 · 端末内検証",
    "library_title": "ライブラリ",
    "library_subtitle": "ローカル封印ファイル · いつでもオフライン検証",
    "library_empty": "録音はまだありません",
    "verify_title": "改ざん検証",
    "verify_subtitle": "任意の WAV を選び、seal chunk をオフラインで解析・検証します。",
    "verify_pick": "ファイルを選択",
    "verify_clear": "レポートを消去",
    "msg_sealed": "封印して保存しました",
    "msg_repaired": "未完了録音を修復して封印しました",
    "state_idle": "待機",
    "state_recording": "封印中 · ハッシュ稼働",
    "state_paused": "一時停止",
    "state_finalizing": "署名パッケージ作成中…",
    "action_verify": "検証",
    "key_fingerprint": "鍵指紋 %1$s",
    "last_verify": "前回の検証: %1$s",
    "rename_title": "録音名を変更",
    "rename_label": "ファイル名",
    "rename_confirm": "保存",
    "rename_cancel": "キャンセル",
    "rename_ok": "名前を変更しました",
    "rename_fail": "名前の変更に失敗（無効または重複）",
    "play_fail": "再生失敗: %1$s",
    "play_busy_recording": "封印録音中は再生できません",
    "record_too_short": "録音が短すぎるか空のため破棄しました",
    "record_failed": "録音に失敗しました",
    "export_verify_ok": "WAV と検証 JSON を書き出しました",
    "tab_settings": "設定",
    "settings_language": "言語",
    "settings_night": "ナイトモード",
    "settings_quality": "録音品質",
    "settings_notif_sounds": "録音中に通知音を許可",
    "settings_trash": "ゴミ箱（%1$d）",
    "settings_about": "SealRec について",
    "lang_system": "システムに従う",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "システムに従う",
    "night_light": "ライト",
    "night_dark": "ダーク",
    "quality_16k": "16 kHz 音声",
    "quality_44k": "44.1 kHz CD",
    "quality_48k": "48 kHz スタジオ",
    "trash_title": "ゴミ箱",
    "trash_empty": "ゴミ箱は空です",
    "trash_restore": "復元",
    "trash_purge": "完全に削除",
    "trash_empty_all": "ゴミ箱を空にする",
    "about_title": "SealRec について",
    "about_proves_title": "SealRec が証明できること",
    "about_proves_body": "録音中にサンプル単位でハッシュし、この端末から離れないハードウェア鍵で署名します。緑の検証結果は、音声バイトがマイク取得時と同一であり、署名がこの端末の鍵によることを証明します。",
    "about_not_title": "証明できないこと",
    "about_not_body": "話者の身元、録音場所、音そのものが演技やスピーカー再生でないことは証明できません。封印後にデジタルファイルが改ざんされていないことだけを証明します。",
    "about_time_title": "DeviceTime について",
    "about_time_body": "封印に埋め込まれた時刻は端末のローカル時計由来で、信頼できる時刻認証局（TSA）ではありません。時計がずれていれば封印時刻もずれます。録音時刻の証明ではなく参考情報として扱ってください。",
}

ko = {
    "notif_channel": "봉인 녹음",
    "notif_recording": "봉인 녹음 중",
    "notif_paused": "녹음 일시중지",
    "action_pause": "일시중지",
    "action_resume": "계속",
    "action_stop": "중지 후 봉인",
    "tab_record": "녹음",
    "tab_library": "보관함",
    "tab_verify": "검증",
    "device_time_disclaimer": "타임스탬프는 기기 로컬 시간이며 신뢰할 수 있는 TSA가 아닙니다.",
    "status_intact": "녹: 변조 없음",
    "status_tampered": "적: 오디오 바이트 변조",
    "status_bad_sig": "황: 서명 오류 / 키 불일치",
    "status_not_seal": "회: SealRec 파일 아님",
    "incomplete_title": "미완료 녹음 발견",
    "incomplete_repair": "복구 후 봉인",
    "incomplete_discard": "폐기",
    "permission_mic": "봉인 녹음에는 마이크 권한이 필요합니다",
    "permission_notif": "포그라운드 서비스 알림 권한이 필요합니다",
    "export_ok": "Music/SealRec으로 내보냄",
    "export_fail": "내보내기 실패",
    "offline_badge": "완전 오프라인 · 네트워크 권한 없음",
    "brand_subtitle": "하드웨어 키 봉인 · 기기 내 검증",
    "library_title": "보관함",
    "library_subtitle": "로컬 봉인 파일 · 언제든 오프라인 검증",
    "library_empty": "녹음 없음",
    "verify_title": "무결성 검증",
    "verify_subtitle": "WAV를 선택해 seal chunk를 오프라인으로 파싱·검증합니다.",
    "verify_pick": "파일 선택",
    "verify_clear": "보고서 지우기",
    "msg_sealed": "봉인되어 저장됨",
    "msg_repaired": "미완료 녹음을 복구해 봉인함",
    "state_idle": "준비",
    "state_recording": "봉인 중 · 해시 엔진 동작",
    "state_paused": "일시중지",
    "state_finalizing": "서명 패키징 중…",
    "action_verify": "검증",
    "key_fingerprint": "키 지문 %1$s",
    "last_verify": "최근 검증: %1$s",
    "rename_title": "녹음 이름 바꾸기",
    "rename_label": "파일 이름",
    "rename_confirm": "저장",
    "rename_cancel": "취소",
    "rename_ok": "이름 변경됨",
    "rename_fail": "이름 변경 실패(잘못되었거나 중복)",
    "play_fail": "재생 실패: %1$s",
    "play_busy_recording": "봉인 녹음 중에는 재생할 수 없습니다",
    "record_too_short": "녹음이 너무 짧거나 비어 폐기됨",
    "record_failed": "녹음 실패",
    "export_verify_ok": "WAV와 검증 JSON을 내보냄",
    "tab_settings": "설정",
    "settings_language": "언어",
    "settings_night": "야간 모드",
    "settings_quality": "녹음 품질",
    "settings_notif_sounds": "녹음 중 알림음 허용",
    "settings_trash": "휴지통(%1$d)",
    "settings_about": "SealRec 정보",
    "lang_system": "시스템 따름",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "시스템 따름",
    "night_light": "밝게",
    "night_dark": "어둡게",
    "quality_16k": "16 kHz 음성",
    "quality_44k": "44.1 kHz CD",
    "quality_48k": "48 kHz 스튜디오",
    "trash_title": "휴지통",
    "trash_empty": "휴지통이 비어 있습니다",
    "trash_restore": "복원",
    "trash_purge": "영구 삭제",
    "trash_empty_all": "휴지통 비우기",
    "about_title": "SealRec 정보",
    "about_proves_title": "SealRec이 증명하는 것",
    "about_proves_body": "녹음 중 샘플 단위로 해시하고, 이 기기를 떠나지 않는 하드웨어 키로 서명합니다. 녹색 검증 결과는 오디오 바이트가 마이크 캡처와 동일하며 서명이 이 기기 키로 만들어졌음을 증명합니다.",
    "about_not_title": "증명하지 못하는 것",
    "about_not_body": "화자 신원, 녹음 장소, 소리가 연출이나 스피커 재생이 아님은 증명할 수 없습니다. 봉인 이후 디지털 파일이 변조되지 않았음만 증명합니다.",
    "about_time_title": "DeviceTime 안내",
    "about_time_body": "봉인에 들어간 시각은 기기 로컬 시계이며 신뢰 시점 기관(TSA)이 아닙니다. 시계가 틀리면 봉인 시각도 틀립니다. 녹음 시각의 증명이 아니라 참고로만 보세요.",
}

es = {
    "notif_channel": "Grabación sellada",
    "notif_recording": "Sellado en curso",
    "notif_paused": "Grabación en pausa",
    "action_pause": "Pausa",
    "action_resume": "Reanudar",
    "action_stop": "Detener y sellar",
    "tab_record": "Grabar",
    "tab_library": "Biblioteca",
    "tab_verify": "Verificar",
    "device_time_disclaimer": "La marca de tiempo es la hora local del dispositivo (no es un TSA de confianza).",
    "status_intact": "VERDE: Íntegro",
    "status_tampered": "ROJO: Audio alterado",
    "status_bad_sig": "AMARILLO: Firma inválida / clave distinta",
    "status_not_seal": "GRIS: No es un archivo SealRec",
    "incomplete_title": "Grabación incompleta encontrada",
    "incomplete_repair": "Reparar y sellar",
    "incomplete_discard": "Descartar",
    "permission_mic": "Se necesita permiso de micrófono",
    "permission_notif": "Se necesita permiso de notificaciones para el servicio en primer plano",
    "export_ok": "Exportado a Music/SealRec",
    "export_fail": "Error al exportar",
    "offline_badge": "Totalmente sin red · sin permiso de internet",
    "brand_subtitle": "Sellado con clave de hardware · verificación en el dispositivo",
    "library_title": "Biblioteca",
    "library_subtitle": "Archivos sellados locales · verificación sin conexión",
    "library_empty": "Aún no hay grabaciones",
    "verify_title": "Comprobación de integridad",
    "verify_subtitle": "Elige un WAV para analizar el seal chunk y verificar sin conexión.",
    "verify_pick": "Elegir archivo",
    "verify_clear": "Borrar informe",
    "msg_sealed": "Sellado y guardado",
    "msg_repaired": "Grabación incompleta reparada y sellada",
    "state_idle": "Listo",
    "state_recording": "Sellando · motor hash activo",
    "state_paused": "En pausa",
    "state_finalizing": "Firmando el paquete…",
    "action_verify": "Verificar",
    "key_fingerprint": "Huella de clave %1$s",
    "last_verify": "Última verificación: %1$s",
    "rename_title": "Renombrar grabación",
    "rename_label": "Nombre de archivo",
    "rename_confirm": "Guardar",
    "rename_cancel": "Cancelar",
    "rename_ok": "Renombrado",
    "rename_fail": "Error al renombrar (inválido o duplicado)",
    "play_fail": "Error de reproducción: %1$s",
    "play_busy_recording": "No se puede reproducir mientras se sella",
    "record_too_short": "Grabación descartada: demasiado corta o vacía",
    "record_failed": "Error de grabación",
    "export_verify_ok": "WAV + JSON de verificación exportados",
    "tab_settings": "Ajustes",
    "settings_language": "Idioma",
    "settings_night": "Modo noche",
    "settings_quality": "Calidad de grabación",
    "settings_notif_sounds": "Permitir sonidos de notificación al grabar",
    "settings_trash": "Papelera (%1$d)",
    "settings_about": "Acerca de SealRec",
    "lang_system": "Sistema",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "Sistema",
    "night_light": "Claro",
    "night_dark": "Oscuro",
    "quality_16k": "16 kHz voz",
    "quality_44k": "44.1 kHz CD",
    "quality_48k": "48 kHz estudio",
    "trash_title": "Papelera",
    "trash_empty": "La papelera está vacía",
    "trash_restore": "Restaurar",
    "trash_purge": "Eliminar para siempre",
    "trash_empty_all": "Vaciar papelera",
    "about_title": "Acerca de SealRec",
    "about_proves_title": "Qué demuestra SealRec",
    "about_proves_body": "Cada grabación se hashea muestra a muestra y se firma con una clave respaldada por hardware que no sale de este dispositivo. Un resultado VERDE prueba que los bytes de audio son exactamente los del micrófono y que la firma es de la clave de este dispositivo.",
    "about_not_title": "Qué NO demuestra",
    "about_not_body": "SealRec no puede demostrar quién habló, dónde ocurrió ni que el sonido no fue escenificado o reproducido por un altavoz. Solo demuestra que el archivo digital no se alteró desde el sellado.",
    "about_time_title": "Sobre DeviceTime",
    "about_time_body": "La marca de tiempo del sello viene del reloj local del dispositivo, no de una autoridad de sellado de tiempo (TSA). Si el reloj estaba mal, el tiempo sellado también. Úsalo como pista, no como prueba de cuándo se grabó.",
}

fr = {
    "notif_channel": "Enregistrement scellé",
    "notif_recording": "Scellage en cours",
    "notif_paused": "Enregistrement en pause",
    "action_pause": "Pause",
    "action_resume": "Reprendre",
    "action_stop": "Arrêter et sceller",
    "tab_record": "Enregistrer",
    "tab_library": "Bibliothèque",
    "tab_verify": "Vérifier",
    "device_time_disclaimer": "L'horodatage est l'heure locale de l'appareil (pas une TSA de confiance).",
    "status_intact": "VERT : Intact",
    "status_tampered": "ROUGE : Octets audio altérés",
    "status_bad_sig": "JAUNE : Signature invalide / clé différente",
    "status_not_seal": "GRIS : Pas un fichier SealRec",
    "incomplete_title": "Enregistrement incomplet trouvé",
    "incomplete_repair": "Réparer et sceller",
    "incomplete_discard": "Abandonner",
    "permission_mic": "Autorisation micro requise",
    "permission_notif": "Autorisation de notification requise pour le service au premier plan",
    "export_ok": "Exporté vers Music/SealRec",
    "export_fail": "Échec de l'export",
    "offline_badge": "Entièrement hors ligne · aucune permission réseau",
    "brand_subtitle": "Scellé par clé matérielle · vérification sur l'appareil",
    "library_title": "Bibliothèque",
    "library_subtitle": "Fichiers scellés locaux · vérification hors ligne",
    "library_empty": "Aucun enregistrement",
    "verify_title": "Contrôle d'intégrité",
    "verify_subtitle": "Choisissez un WAV pour analyser le seal chunk et vérifier hors ligne.",
    "verify_pick": "Choisir un fichier",
    "verify_clear": "Effacer le rapport",
    "msg_sealed": "Scellé et enregistré",
    "msg_repaired": "Enregistrement incomplet réparé et scellé",
    "state_idle": "Prêt",
    "state_recording": "Scellage · moteur de hachage actif",
    "state_paused": "En pause",
    "state_finalizing": "Signature du paquet…",
    "action_verify": "Vérifier",
    "key_fingerprint": "Empreinte de clé %1$s",
    "last_verify": "Dernière vérification : %1$s",
    "rename_title": "Renommer l'enregistrement",
    "rename_label": "Nom de fichier",
    "rename_confirm": "Enregistrer",
    "rename_cancel": "Annuler",
    "rename_ok": "Renommé",
    "rename_fail": "Échec du renommage (invalide ou doublon)",
    "play_fail": "Échec de lecture : %1$s",
    "play_busy_recording": "Lecture impossible pendant le scellage",
    "record_too_short": "Enregistrement rejeté : trop court ou vide",
    "record_failed": "Échec de l'enregistrement",
    "export_verify_ok": "WAV + JSON de vérification exportés",
    "tab_settings": "Réglages",
    "settings_language": "Langue",
    "settings_night": "Mode nuit",
    "settings_quality": "Qualité d'enregistrement",
    "settings_notif_sounds": "Autoriser les sons de notification pendant l'enregistrement",
    "settings_trash": "Corbeille (%1$d)",
    "settings_about": "À propos de SealRec",
    "lang_system": "Système",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "Système",
    "night_light": "Clair",
    "night_dark": "Sombre",
    "quality_16k": "16 kHz voix",
    "quality_44k": "44,1 kHz CD",
    "quality_48k": "48 kHz studio",
    "trash_title": "Corbeille",
    "trash_empty": "La corbeille est vide",
    "trash_restore": "Restaurer",
    "trash_purge": "Supprimer définitivement",
    "trash_empty_all": "Vider la corbeille",
    "about_title": "À propos de SealRec",
    "about_proves_title": "Ce que SealRec prouve",
    "about_proves_body": "Chaque enregistrement est haché échantillon par échantillon et signé avec une clé matérielle qui ne quitte jamais cet appareil. Un résultat VERT prouve que les octets audio sont exactement ceux du micro et que la signature vient de la clé de cet appareil.",
    "about_not_title": "Ce qu'il ne prouve PAS",
    "about_not_body": "SealRec ne peut pas prouver qui parlait, où cela s'est passé, ni que le son n'était pas mis en scène ou rejoué. Il prouve seulement que le fichier numérique n'a pas été altéré depuis le scellage.",
    "about_time_title": "À propos de DeviceTime",
    "about_time_body": "L'horodatage du sceau vient de l'horloge locale de l'appareil, pas d'une autorité d'horodatage (TSA). Si l'horloge était fausse, l'heure scellée le sera aussi. Traitez-la comme un indice, pas comme une preuve de quand l'enregistrement a été fait.",
}

de = {
    "notif_channel": "Versiegelte Aufnahme",
    "notif_recording": "Versiegelung läuft",
    "notif_paused": "Aufnahme pausiert",
    "action_pause": "Pause",
    "action_resume": "Fortsetzen",
    "action_stop": "Stoppen und versiegeln",
    "tab_record": "Aufnehmen",
    "tab_library": "Bibliothek",
    "tab_verify": "Prüfen",
    "device_time_disclaimer": "Der Zeitstempel ist die lokale Gerätezeit (kein vertrauenswürdiger TSA).",
    "status_intact": "GRÜN: Unversehrt",
    "status_tampered": "ROT: Audio-Bytes verändert",
    "status_bad_sig": "GELB: Ungültige Signatur / Schlüssel stimmt nicht",
    "status_not_seal": "GRAU: Keine SealRec-Datei",
    "incomplete_title": "Unvollständige Aufnahme gefunden",
    "incomplete_repair": "Reparieren und versiegeln",
    "incomplete_discard": "Verwerfen",
    "permission_mic": "Mikrofonberechtigung erforderlich",
    "permission_notif": "Benachrichtigungsberechtigung für den Vordergrunddienst erforderlich",
    "export_ok": "Nach Music/SealRec exportiert",
    "export_fail": "Export fehlgeschlagen",
    "offline_badge": "Vollständig offline · keine Netzwerkberechtigung",
    "brand_subtitle": "Hardware-Schlüssel versiegelt · Prüfung auf dem Gerät",
    "library_title": "Bibliothek",
    "library_subtitle": "Lokale versiegelte Dateien · jederzeit offline prüfen",
    "library_empty": "Noch keine Aufnahmen",
    "verify_title": "Integritätsprüfung",
    "verify_subtitle": "Wähle eine WAV-Datei, um den seal-Chunk offline zu prüfen.",
    "verify_pick": "Datei wählen",
    "verify_clear": "Bericht löschen",
    "msg_sealed": "Versiegelt und gespeichert",
    "msg_repaired": "Unvollständige Aufnahme repariert und versiegelt",
    "state_idle": "Bereit",
    "state_recording": "Versiegeln · Hash-Engine aktiv",
    "state_paused": "Pausiert",
    "state_finalizing": "Paket wird signiert…",
    "action_verify": "Prüfen",
    "key_fingerprint": "Schlüsselfingerabdruck %1$s",
    "last_verify": "Letzte Prüfung: %1$s",
    "rename_title": "Aufnahme umbenennen",
    "rename_label": "Dateiname",
    "rename_confirm": "Speichern",
    "rename_cancel": "Abbrechen",
    "rename_ok": "Umbenannt",
    "rename_fail": "Umbenennen fehlgeschlagen (ungültig oder doppelt)",
    "play_fail": "Wiedergabe fehlgeschlagen: %1$s",
    "play_busy_recording": "Während der Versiegelung keine Wiedergabe",
    "record_too_short": "Aufnahme verworfen: zu kurz oder leer",
    "record_failed": "Aufnahme fehlgeschlagen",
    "export_verify_ok": "WAV + Prüf-JSON exportiert",
    "tab_settings": "Einstellungen",
    "settings_language": "Sprache",
    "settings_night": "Nachtmodus",
    "settings_quality": "Aufnahmequalität",
    "settings_notif_sounds": "Benachrichtigungstöne während der Aufnahme erlauben",
    "settings_trash": "Papierkorb (%1$d)",
    "settings_about": "Über SealRec",
    "lang_system": "System",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "System",
    "night_light": "Hell",
    "night_dark": "Dunkel",
    "quality_16k": "16 kHz Sprache",
    "quality_44k": "44,1 kHz CD",
    "quality_48k": "48 kHz Studio",
    "trash_title": "Papierkorb",
    "trash_empty": "Papierkorb ist leer",
    "trash_restore": "Wiederherstellen",
    "trash_purge": "Endgültig löschen",
    "trash_empty_all": "Papierkorb leeren",
    "about_title": "Über SealRec",
    "about_proves_title": "Was SealRec beweist",
    "about_proves_body": "Jede Aufnahme wird sampleweise gehasht und mit einem hardwaregestützten Schlüssel signiert, der dieses Gerät nie verlässt. Ein GRÜNES Ergebnis beweist, dass die Audio-Bytes exakt dem Mikrofon entsprechen und die Signatur von diesem Geräteschlüssel stammt.",
    "about_not_title": "Was es NICHT beweist",
    "about_not_body": "SealRec kann nicht beweisen, wer sprach, wo aufgenommen wurde, oder dass der Klang nicht gestellt bzw. über Lautsprecher abgespielt wurde. Es beweist nur, dass die digitale Datei seit der Versiegelung unverändert ist.",
    "about_time_title": "Zu DeviceTime",
    "about_time_body": "Der eingebettete Zeitstempel kommt von der lokalen Geräteuhr, nicht von einer vertrauenswürdigen Zeitstempel-Stelle (TSA). War die Uhr falsch, ist auch die versiegelte Zeit falsch. Als Hinweis nutzen, nicht als Beweis des Aufnahmezeitpunkts.",
}

pt = {
    "notif_channel": "Gravação selada",
    "notif_recording": "Selagem em andamento",
    "notif_paused": "Gravação pausada",
    "action_pause": "Pausar",
    "action_resume": "Retomar",
    "action_stop": "Parar e selar",
    "tab_record": "Gravar",
    "tab_library": "Biblioteca",
    "tab_verify": "Verificar",
    "device_time_disclaimer": "O carimbo de tempo é o horário local do dispositivo (não é um TSA confiável).",
    "status_intact": "VERDE: Íntegro",
    "status_tampered": "VERMELHO: Áudio adulterado",
    "status_bad_sig": "AMARELO: Assinatura inválida / chave diferente",
    "status_not_seal": "CINZA: Não é um arquivo SealRec",
    "incomplete_title": "Gravação incompleta encontrada",
    "incomplete_repair": "Reparar e selar",
    "incomplete_discard": "Descartar",
    "permission_mic": "Permissão de microfone necessária",
    "permission_notif": "Permissão de notificação necessária para o serviço em primeiro plano",
    "export_ok": "Exportado para Music/SealRec",
    "export_fail": "Falha na exportação",
    "offline_badge": "Totalmente offline · sem permissão de rede",
    "brand_subtitle": "Selado com chave de hardware · verificação no dispositivo",
    "library_title": "Biblioteca",
    "library_subtitle": "Arquivos selados locais · verificação offline",
    "library_empty": "Nenhuma gravação ainda",
    "verify_title": "Verificação de integridade",
    "verify_subtitle": "Escolha um WAV para analisar o seal chunk e verificar offline.",
    "verify_pick": "Escolher arquivo",
    "verify_clear": "Limpar relatório",
    "msg_sealed": "Selado e salvo",
    "msg_repaired": "Gravação incompleta reparada e selada",
    "state_idle": "Pronto",
    "state_recording": "Selando · motor de hash ativo",
    "state_paused": "Pausado",
    "state_finalizing": "Assinando o pacote…",
    "action_verify": "Verificar",
    "key_fingerprint": "Impressão da chave %1$s",
    "last_verify": "Última verificação: %1$s",
    "rename_title": "Renomear gravação",
    "rename_label": "Nome do arquivo",
    "rename_confirm": "Salvar",
    "rename_cancel": "Cancelar",
    "rename_ok": "Renomeado",
    "rename_fail": "Falha ao renomear (inválido ou duplicado)",
    "play_fail": "Falha na reprodução: %1$s",
    "play_busy_recording": "Não é possível reproduzir durante a selagem",
    "record_too_short": "Gravação descartada: muito curta ou vazia",
    "record_failed": "Falha na gravação",
    "export_verify_ok": "WAV + JSON de verificação exportados",
    "tab_settings": "Configurações",
    "settings_language": "Idioma",
    "settings_night": "Modo noturno",
    "settings_quality": "Qualidade de gravação",
    "settings_notif_sounds": "Permitir sons de notificação ao gravar",
    "settings_trash": "Lixeira (%1$d)",
    "settings_about": "Sobre o SealRec",
    "lang_system": "Sistema",
    "lang_en": "English",
    "lang_zh": "中文",
    "night_system": "Sistema",
    "night_light": "Claro",
    "night_dark": "Escuro",
    "quality_16k": "16 kHz voz",
    "quality_44k": "44,1 kHz CD",
    "quality_48k": "48 kHz estúdio",
    "trash_title": "Lixeira",
    "trash_empty": "A lixeira está vazia",
    "trash_restore": "Restaurar",
    "trash_purge": "Excluir para sempre",
    "trash_empty_all": "Esvaziar lixeira",
    "about_title": "Sobre o SealRec",
    "about_proves_title": "O que o SealRec prova",
    "about_proves_body": "Cada gravação é hasheada amostra a amostra e assinada com uma chave de hardware que nunca sai deste dispositivo. Um resultado VERDE prova que os bytes de áudio são exatamente os do microfone e que a assinatura veio da chave deste dispositivo.",
    "about_not_title": "O que NÃO prova",
    "about_not_body": "O SealRec não pode provar quem falou, onde foi gravado, nem que o som não foi encenado ou reproduzido por um alto-falante. Ele só prova que o arquivo digital não foi alterado desde a selagem.",
    "about_time_title": "Sobre DeviceTime",
    "about_time_body": "O carimbo embutido no selo vem do relógio local do dispositivo, não de uma autoridade de carimbo de tempo (TSA). Se o relógio estava errado, o tempo selado também estará. Trate como pista, não como prova de quando a gravação foi feita.",
}

write_locale("values-ja", ja)
write_locale("values-ko", ko)
write_locale("values-es", es)
write_locale("values-fr", fr)
write_locale("values-de", de)
write_locale("values-pt-rBR", pt)
print("done")
