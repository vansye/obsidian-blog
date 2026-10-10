package luv.vansye.read;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReadMapper {

    String COLUMNS = "plate, slug, section, title, description, "
            + "created_at, updated_at, word_count, read_minutes, tags";

    @Select("SELECT " + COLUMNS + ", body_html FROM post "
            + "WHERE plate = #{plate} AND status = 'published'")
    PostRow findByPlate(@Param("plate") String plate);

    @Select("SELECT " + COLUMNS + ", body_html FROM post "
            + "WHERE slug = #{slug} AND status = 'published'")
    PostRow findBySlug(@Param("slug") String slug);

    @Select("SELECT " + COLUMNS + ", body_html FROM post "
            + "WHERE section = #{section} AND status = 'published' "
            + "ORDER BY id DESC LIMIT 1")
    PostRow findOneBySection(@Param("section") String section);

    @Select("SELECT " + COLUMNS + " FROM post "
            + "WHERE section = #{section} AND status = 'published' "
            + "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PostRow> listBySection(@Param("section") String section,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM post WHERE section = #{section} AND status = 'published'")
    int countBySection(@Param("section") String section);

    @Select("SELECT " + COLUMNS + " FROM post "
            + "WHERE status = 'published' AND section NOT IN ('ABOUT', 'LINKS') "
            + "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<PostRow> listIndex(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM post "
            + "WHERE status = 'published' AND section NOT IN ('ABOUT', 'LINKS')")
    int countIndex();

    @Select("SELECT " + COLUMNS + " FROM post "
            + "WHERE status = 'published' "
            + "  AND section = (SELECT section FROM post "
            + "                 WHERE plate = #{plate} AND status = 'published') "
            + "  AND (created_at, id) > (SELECT created_at, id FROM post "
            + "                             WHERE plate = #{plate} AND status = 'published') "
            + "ORDER BY created_at ASC, id ASC LIMIT 1")
    PostRow findNewerInSection(@Param("plate") String plate);

    @Select("SELECT " + COLUMNS + " FROM post "
            + "WHERE status = 'published' "
            + "  AND section = (SELECT section FROM post "
            + "                 WHERE plate = #{plate} AND status = 'published') "
            + "  AND (created_at, id) < (SELECT created_at, id FROM post "
            + "                             WHERE plate = #{plate} AND status = 'published') "
            + "ORDER BY created_at DESC, id DESC LIMIT 1")
    PostRow findOlderInSection(@Param("plate") String plate);
}
