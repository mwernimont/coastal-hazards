var Session = function(name, isPerm) {
    var me = (this === window) ? {} : this;
    
    me.name = (!name) ? randomUUID() : name;
    me.sessionObject = isPerm ? localStorage : sessionStorage;
    me.session =  $.parseJSON(me.sessionObject.getItem(me.name));
    
    // Initialize the session object
    if (!me.session && isPerm) {
        var newSession = {};
        var randID = randomUUID();

        newSession[randID] = new Object(); 
        
        me.session = { sessions : {} }
        me.session['sessions'][randID] = newSession;
        me.session['sessions'][randID].files = [];
        me.session['current-session'] = new Object();
        me.session['current-session']['key'] = randID;
        me.session['current-session']['session'] = me.session['sessions'][randID];
        me.sessionObject.setItem(me.name, JSON.stringify(me.session));
    }

    return $.extend(me, {
        save : function() {
            me.sessionObject.setItem(me.name, JSON.stringify(me.session))
        },
        load : function(name) {
            $.parseJSON(me.sessionObject.getItem(me.name));
        },
        addFileToSession : function(token) {
            me.session.sessions[me.getCurrentSessionKey()].files.push(token);
            me.setCurrentSession(me.getCurrentSessionKey());
        },
        getCurrentSessionKey : function() {
            return me.session['current-session'].key;
        },
        setCurrentSession : function(key) {
            me.session['current-session']['key'] = key;
            me.session['current-session']['session'] = me.session['sessions'][key];
            return me.session['current-session'];
        }
    });
}



function randomGUID() {
    var S4 = function() {
        return (((1+Math.random())*0x10000)|0).toString(16).substring(1);
    }
    
    return (S4()+S4()+"-"+S4()+"-"+S4()+"-"+S4()+"-"+S4()+S4()+S4());
}

function randomUUID() {
  var s = [], itoh = '0123456789ABCDEF';
 
  // Make array of random hex digits. The UUID only has 32 digits in it, but we
  // allocate an extra items to make room for the '-'s we'll be inserting.
  for (var i = 0; i <36; i++) s[i] = Math.floor(Math.random()*0x10);
 
  // Conform to RFC-4122, section 4.4
  s[14] = 4;  // Set 4 high bits of time_high field to version
  s[19] = (s[19] & 0x3) | 0x8;  // Specify 2 high bits of clock sequence
 
  // Convert to hex chars
  for (var i = 0; i <36; i++) s[i] = itoh[s[i]];
 
  // Insert '-'s
  s[8] = s[13] = s[18] = s[23] = '-';
 
  return s.join('');
}
