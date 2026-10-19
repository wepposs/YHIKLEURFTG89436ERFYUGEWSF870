package vesence.module.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Retention(RetentionPolicy.RUNTIME)
@Environment(EnvType.CLIENT)
public @interface IModule {
   String name();

   String description() default "У модуля нет описания";

   Category category();

   int bind() default -1;
}
