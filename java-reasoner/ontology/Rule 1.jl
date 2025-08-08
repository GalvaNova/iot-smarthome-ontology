Rule 1

ACdp_hasPPMvalue(?rgas, ?lpg)^
swrlb:greaterThan(?lpg, 700)^
M_hasAction(?buzz, act_AC_Buzzer) 
-> M_hasActionStatus(act_AC_Buzzer, st_actON)

Rule 2

ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 0)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:greaterThanOrEqual(?dist, 10)^
M_hasAction(?buzz, act_AC_Buzzer) 
-> M_hasActionStatus(act_AC_Buzzer, st_actON)

Rule 3

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:greaterThan(?temp, 35)^
ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 0)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:lessThan(?dist, 10)^
M_hasActivity(?a, fnc_cookAct) 
-> M_hasActivityStatus(fnc_cookAct, st_cookYES)

Rule 4

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:lessThanOrEqual(?temp, 35)^
ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 0)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:greaterThanOrEqual(?dist, 10)^
M_hasActivity(?a, fnc_cookAct) 
-> M_hasActivityStatus(fnc_cookAct, st_cookYES)

Rule 5

ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 1)^
ACdp_hasPPMvalue(?rgas, ?lpg)^
swrlb:greaterThan(?lpg, 700)^
M_hasAction(?a, act_AC_Exhaust) 
-> M_hasActionStatus(act_AC_Exhaust, st_actON)

Rule 6

ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 1)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:lessThan(?dist, 10)^
M_hasAction(?exhaust, act_AC_Exhaust) 
-> M_hasActionStatus(act_AC_Exhaust, st_actON)

Rule 7

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:greaterThan(?temp, 35)^
ACdp_hasPPMvalue(?rgas, ?lpg)^
swrlb:greaterThan(?lpg, 700)^
M_hasAction(?exhaust, act_AC_Exhaust) 
-> M_hasActionStatus(act_AC_Exhaust, st_actON)

Rule 8

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:greaterThan(?temp, 35)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:lessThan(?dist, 10)^
M_hasAction(?exhaust, act_AC_Exhaust) 
-> M_hasActionStatus(act_AC_Exhaust, st_actON)

Rule 9

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:greaterThan(?temp, 35)^
ACdp_hasTEMPvalue(?rflame, ?flame)^
swrlb:equal(?flame, 0)^
M_hasAction(?exhaust, act_AC_Exhaust) 
-> M_hasActionStatus(act_AC_Exhaust, st_actON)

Rule 10

ACdp_hasTEMPvalue(?rtemp, ?temp)^
swrlb:greaterThan(?temp, 35)^
ACdp_hasFIREvalue(?rflame, ?flame)^
swrlb:equal(?flame, 1)^
ACdp_hasDISTvalue(?rsuls, ?dist)^
swrlb:greaterThanOrEqual(?dist, 10)^
M_hasActivity(?a, fnc_cookAct) 
-> M_hasActivityStatus(fnc_cookAct, st_cookNO)

Rule 11

M_hasActivityStatus(fnc_cookAct, st_cookNO)^
ACop_hasTimer(?time, fnc_timing) 
-> ACop_hasTimer(fnc_timing, st_timerOFF)

Rule 12

M_hasActivityStatus(fnc_cookAct, st_cookYES)^
ACop_hasTimer(?time, fnc_timing) 
-> ACop_hasTimer(fnc_timing, st_timerON)

Rule 13

AEdp_hasCOUNTvalue(?pers, ?counting)^
swrlb:greaterThan(?counting, 0)^
M_hasAction(?inout, act_AE_Lamp) 
-> M_hasActionStatus(act_AE_Lamp, st_actON)

Rule 14

ASdp_hasDISTOBJvalue(?robj, ?valobj)^
ASdp_hasDISTPERSvalue(?rpers, ?valpers)^
swrlb:lessThan(?valobj, 20)^
swrlb:lessThan(?valpers, 20)^
M_hasActivity(?actv, fnc_washAct)^
M_hasAction(?valve, act_AS_Valve) 
-> M_hasActionStatus(act_AS_Valve, st_actON)^
M_hasActivityStatus(fnc_washAct, st_washYES)

Rule 15

M_hasActivityStatus(fnc_cookAct, st_cookYES)^
AEdp_hasCOUNTvalue(?pers, ?counting)^
swrlb:equal(?counting, 0)^
M_hasAction(?inout, act_AE_Lamp) 
-> M_hasActionStatus(act_AE_Lamp, st_actON)


