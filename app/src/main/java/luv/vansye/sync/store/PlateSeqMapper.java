package luv.vansye.sync.store;

import luv.vansye.sync.domain.Section;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PlateSeqMapper {

    static String seqNameOf(Section section) {
        return "plate:" + section.platePrefix();
    }

    @Update("UPDATE plate_seq SET value = value + 1 WHERE name = #{name}")
    int increment(@Param("name") String name);

    @Select("SELECT value FROM plate_seq WHERE name = #{name}")
    Long current(@Param("name") String name);

    @Update("UPDATE plate_seq SET value = #{value} WHERE name = #{name}")
    int setValue(@Param("name") String name, @Param("value") long value);
}
