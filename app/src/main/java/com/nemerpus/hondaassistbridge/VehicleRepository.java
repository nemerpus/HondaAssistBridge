package com.nemerpus.hondaassistbridge;
import android.content.Context;
import org.json.*;
import java.util.*;

final class VehicleRepository {
    private static final String KEY="vehicle_profiles_v1", ACTIVE="active_vehicle_id";

    static synchronized List<VehicleProfile> load(Context c){
        migrate(c);
        ArrayList<VehicleProfile> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(AppConfig.prefs(c).getString(KEY,"[]"));
            for(int i=0;i<a.length();i++) out.add(VehicleProfile.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        return out;
    }

    static synchronized VehicleProfile active(Context c){
        List<VehicleProfile> all=load(c); if(all.isEmpty()) return null;
        String id=AppConfig.prefs(c).getString(ACTIVE,"");
        for(VehicleProfile v:all) if(v.id.equals(id)) return v;
        return all.get(0);
    }

    static synchronized void save(Context c,VehicleProfile v){
        List<VehicleProfile> all=load(c); boolean found=false;
        for(int i=0;i<all.size();i++) if(all.get(i).id.equals(v.id)){all.set(i,v);found=true;break;}
        if(!found) all.add(v);
        JSONArray a=new JSONArray(); for(VehicleProfile x:all)a.put(x.toJson());
        AppConfig.prefs(c).edit().putString(KEY,a.toString()).putString(ACTIVE,v.id).apply();
    }

    static synchronized VehicleProfile create(Context c){
        VehicleProfile v=new VehicleProfile("vehicle-"+System.currentTimeMillis());
        v.brand="Honda";v.model="";v.registration="";v.nickname="";v.deviceName="HONDA BTU";v.primary=load(c).isEmpty();save(c,v);return v;
    }

    private static void migrate(Context c){
        if(AppConfig.prefs(c).contains(KEY))return;
        VehicleProfile v=new VehicleProfile("vehicle-legacy");
        v.brand="Honda";v.model="";v.registration="";v.nickname="";
        v.deviceName=AppConfig.getDeviceFilter(c);v.deviceAddress=AppConfig.getCompanionAddress(c);v.primary=true;
        JSONArray a=new JSONArray();a.put(v.toJson());
        AppConfig.prefs(c).edit().putString(KEY,a.toString()).putString(ACTIVE,v.id).apply();
        FlightRecorder.log(c,"MIGRATION","v0.10 -> VehicleProfile local");
    }
    private VehicleRepository(){}
}
