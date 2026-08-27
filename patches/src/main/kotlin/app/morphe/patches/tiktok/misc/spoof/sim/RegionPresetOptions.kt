/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.spoof.sim

internal data class RegionPresetOption(
    val id: String,
    val country: String,
    val iso: String,
    val mccMnc: String,
    val operatorName: String,
) {
    val label = "$country ($operatorName)"
}

internal const val DEFAULT_REGION_PRESET_ID = "ae_du"

internal val regionPresetOptions = listOf(
    RegionPresetOption("ae_du", "United Arab Emirates", "ae", "42403", "du"),
    RegionPresetOption("ae_etisalat", "United Arab Emirates", "ae", "42402", "Etisalat"),
    RegionPresetOption("us_t_mobile", "United States", "us", "310260", "T-Mobile"),
    RegionPresetOption("gb_ee", "United Kingdom", "gb", "23430", "EE"),
    RegionPresetOption("ca_rogers", "Canada", "ca", "302720", "Rogers"),
    RegionPresetOption("ru_mts", "Russia", "ru", "25001", "MTS"),
    RegionPresetOption("de_telekom_de", "Germany", "de", "26201", "Telekom.de"),
    RegionPresetOption("fr_orange", "France", "fr", "20801", "Orange"),
    RegionPresetOption("it_tim", "Italy", "it", "22201", "TIM"),
    RegionPresetOption("es_movistar", "Spain", "es", "21407", "Movistar"),
    RegionPresetOption("nl_kpn", "Netherlands", "nl", "20408", "KPN"),
    RegionPresetOption("pl_orange", "Poland", "pl", "26003", "Orange"),
    RegionPresetOption("pt_meo", "Portugal", "pt", "26806", "MEO"),
    RegionPresetOption("be_proximus", "Belgium", "be", "20601", "Proximus"),
    RegionPresetOption("ch_swisscom", "Switzerland", "ch", "22801", "Swisscom"),
    RegionPresetOption("at_a1", "Austria", "at", "23201", "A1"),
    RegionPresetOption("se_telia", "Sweden", "se", "24001", "Telia"),
    RegionPresetOption("no_telenor", "Norway", "no", "24201", "Telenor"),
    RegionPresetOption("dk_tdc", "Denmark", "dk", "23801", "TDC"),
    RegionPresetOption("gr_cosmote", "Greece", "gr", "20201", "Cosmote"),
    RegionPresetOption("ua_kyivstar", "Ukraine", "ua", "25503", "Kyivstar"),
    RegionPresetOption("ro_orange", "Romania", "ro", "22610", "Orange"),
    RegionPresetOption("cz_t_mobile", "Czech Republic", "cz", "23001", "T-Mobile"),
    RegionPresetOption("hu_magyar_telekom", "Hungary", "hu", "21630", "Magyar Telekom"),
    RegionPresetOption("ie_vodafone", "Ireland", "ie", "27201", "Vodafone"),
    RegionPresetOption("tr_turkcell", "Turkey", "tr", "28601", "Turkcell"),
    RegionPresetOption("sa_stc", "Saudi Arabia", "sa", "42001", "stc"),
    RegionPresetOption("qa_ooredoo", "Qatar", "qa", "42701", "Ooredoo"),
    RegionPresetOption("kw_zain", "Kuwait", "kw", "41902", "Zain"),
    RegionPresetOption("om_omantel", "Oman", "om", "42202", "Omantel"),
    RegionPresetOption("jo_zain", "Jordan", "jo", "41601", "Zain"),
    RegionPresetOption("iq_zain", "Iraq", "iq", "41820", "Zain"),
    RegionPresetOption("lb_alfa", "Lebanon", "lb", "41501", "Alfa"),
    RegionPresetOption("eg_vodafone", "Egypt", "eg", "60202", "Vodafone"),
    RegionPresetOption("ma_maroc_telecom", "Morocco", "ma", "60401", "Maroc Telecom"),
    RegionPresetOption("dz_mobilis", "Algeria", "dz", "60301", "Mobilis"),
    RegionPresetOption("tn_tunisie_telecom", "Tunisia", "tn", "60502", "Tunisie Telecom"),
    RegionPresetOption("in_jio", "India", "in", "405840", "Jio"),
    RegionPresetOption("pk_jazz", "Pakistan", "pk", "41001", "Jazz"),
    RegionPresetOption("bd_grameenphone", "Bangladesh", "bd", "47001", "Grameenphone"),
    RegionPresetOption("lk_dialog", "Sri Lanka", "lk", "41302", "Dialog"),
    RegionPresetOption("np_ncell", "Nepal", "np", "42902", "Ncell"),
    RegionPresetOption("id_telkomsel", "Indonesia", "id", "51010", "Telkomsel"),
    RegionPresetOption("ph_smart", "Philippines", "ph", "51503", "Smart"),
    RegionPresetOption("th_ais", "Thailand", "th", "52003", "AIS"),
    RegionPresetOption("vn_viettel", "Vietnam", "vn", "45204", "Viettel"),
    RegionPresetOption("my_maxis", "Malaysia", "my", "50212", "Maxis"),
    RegionPresetOption("sg_singtel", "Singapore", "sg", "52501", "Singtel"),
    RegionPresetOption("hk_csl", "Hong Kong", "hk", "45400", "CSL"),
    RegionPresetOption("tw_chunghwa_telecom", "Taiwan", "tw", "46692", "Chunghwa Telecom"),
    RegionPresetOption("jp_ntt_docomo", "Japan", "jp", "44010", "NTT DOCOMO"),
    RegionPresetOption("kr_sk_telecom", "South Korea", "kr", "45005", "SK Telecom"),
    RegionPresetOption("au_telstra", "Australia", "au", "50501", "Telstra"),
    RegionPresetOption("nz_spark", "New Zealand", "nz", "53005", "Spark"),
    RegionPresetOption("br_vivo", "Brazil", "br", "72410", "Vivo"),
    RegionPresetOption("mx_telcel", "Mexico", "mx", "334020", "Telcel"),
    RegionPresetOption("ar_claro", "Argentina", "ar", "722310", "Claro"),
    RegionPresetOption("co_claro", "Colombia", "co", "732101", "Claro"),
    RegionPresetOption("cl_entel", "Chile", "cl", "73001", "Entel"),
    RegionPresetOption("pe_claro", "Peru", "pe", "71610", "Claro"),
    RegionPresetOption("za_vodacom", "South Africa", "za", "65501", "Vodacom"),
    RegionPresetOption("ng_mtn", "Nigeria", "ng", "62130", "MTN"),
    RegionPresetOption("ke_safaricom", "Kenya", "ke", "63902", "Safaricom"),
    RegionPresetOption("gh_mtn", "Ghana", "gh", "62001", "MTN"),
    RegionPresetOption("et_ethio_telecom", "Ethiopia", "et", "63601", "Ethio Telecom"),
)

internal val regionPresetOptionValues = regionPresetOptions.associateTo(linkedMapOf()) {
    it.label to it.id
}

internal fun regionPresetOption(id: String): RegionPresetOption =
    regionPresetOptions.first { it.id == id }
