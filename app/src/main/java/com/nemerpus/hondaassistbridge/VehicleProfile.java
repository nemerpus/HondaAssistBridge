package com.nemerpus.hondaassistbridge;
import org.json.JSONObject;

final class VehicleProfile {
    String id, brand, model, registration, nickname, deviceName, deviceAddress;
    boolean primary;

    VehicleProfile(String id){this.id=id;}

    String displayName(){
        String b=brand==null?"":brand.trim(), m=model==null?"":model.trim(), n=nickname==null?"":nickname.trim();
        if(!n.isEmpty()) return n;
        String x=(b+" "+m).trim(); return x.isEmpty()?"Mi motocicleta":x;
    }

    JSONObject toJson(){
        JSONObject o=new JSONObject();
        try{o.put("id",id);o.put("brand",brand);o.put("model",model);o.put("registration",registration);
            o.put("nickname",nickname);o.put("deviceName",deviceName);o.put("deviceAddress",deviceAddress);o.put("primary",primary);}
        catch(Exception ignored){}
        return o;
    }

    static VehicleProfile fromJson(JSONObject o){
        VehicleProfile v=new VehicleProfile(o.optString("id","vehicle-1"));
        v.brand=o.optString("brand","Honda"); v.model=o.optString("model","");
        v.registration=o.optString("registration",""); v.nickname=o.optString("nickname","");
        v.deviceName=o.optString("deviceName","HONDA BTU"); v.deviceAddress=o.optString("deviceAddress","");
        v.primary=o.optBoolean("primary",true); return v;
    }
}
