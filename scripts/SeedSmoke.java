import dev.clans.core.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public class SeedSmoke {
  public static void main(String[] paths) {
    for(String path:paths)try(Store store=new Store(Path.of(path))) {
      if(store.snapshot().named("SmokeAlpha")!=null)continue;
      ClanService service=new ClanService(store,Settings.defaults(),Clock.systemUTC());
      var leader=new ClanService.Actor(UUID.nameUUIDFromBytes("SmokeAlice".getBytes()),"SmokeAlice",false);
      var other=new ClanService.Actor(UUID.nameUUIDFromBytes("SmokeBob".getBytes()),"SmokeBob",false);
      service.execute(leader,"create",new String[]{"SmokeAlpha"},null,null).join();
      service.execute(other,"create",new String[]{"SmokeBravo"},null,null).join();
      service.execute(leader,"open",new String[0],null,null).join();
      for(int n=0;n<14;n++) {
        var member=new ClanService.Actor(UUID.nameUUIDFromBytes(("SmokeMember"+n).getBytes()),"SmokeMember"+n,false);
        service.execute(member,"join",new String[]{"SmokeAlpha"},null,null).join();
        if(n==0)service.execute(leader,"promote",new String[]{member.name()},member.id(),member.name()).join();
      }
      service.execute(leader,"motto",new String[]{"Developer style smoke test"},null,null).join();
      service.execute(leader,"enemy",new String[]{"SmokeBravo"},null,null).join();
      service.execute(other,"peace",new String[]{"SmokeAlpha"},null,null).join();
    }
  }
}
