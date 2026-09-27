package com.nemerpus.hondaassistbridge;

import org.json.JSONException;
import org.json.JSONObject;

final class Command {
    static final String ACTION_ASSISTANT="assistant", ACTION_APP="app", ACTION_DIAL="dial", ACTION_NAVIGATION="navigation", ACTION_MEDIA_PLAY_PAUSE="media_play_pause", ACTION_MEDIA_NEXT="media_next", ACTION_MEDIA_PREVIOUS="media_previous";
    String id,name; boolean enabled=true,doublePress=true; int eventCode=0x03; String actionType=ACTION_ASSISTANT,actionValue="",actionLabel="";
    JSONObject toJson() throws JSONException { JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("enabled",enabled);o.put("doublePress",doublePress);o.put("eventCode",eventCode);o.put("actionType",actionType);o.put("actionValue",actionValue==null?"":actionValue);o.put("actionLabel",actionLabel==null?"":actionLabel);return o; }
    static Command fromJson(JSONObject o){Command c=new Command();c.id=o.optString("id",java.util.UUID.randomUUID().toString());c.name=o.optString("name","Comando");c.enabled=o.optBoolean("enabled",true);c.doublePress=o.optBoolean("doublePress",true);c.eventCode=o.optInt("eventCode",0x03)&255;c.actionType=o.optString("actionType",ACTION_ASSISTANT);c.actionValue=o.optString("actionValue","");c.actionLabel=o.optString("actionLabel","");return c;}
    String gestureLabel(){String arrow=GestureEngine.arrowForCode(eventCode);return doublePress?arrow+"  "+arrow:arrow+"  ━";}
    String gestureDescription(){String d=GestureEngine.directionForCode(eventCode);return doublePress?"Doble "+d:"Mantener "+d;}
}
