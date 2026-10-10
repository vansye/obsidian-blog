package luv.vansye.sync.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {

    @Select("SELECT * FROM post WHERE vault_path = #{vaultPath}")
    PostEntity findByVaultPath(@Param("vaultPath") String vaultPath);

    @Select("SELECT * FROM post WHERE slug = #{slug}")
    PostEntity findBySlug(@Param("slug") String slug);

    @Update("UPDATE post SET slug = NULL WHERE id = #{id}")
    int clearSlug(@Param("id") Long id);

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING_INDEX(plate, '-', -1) AS UNSIGNED)), 0) "
            + "FROM post WHERE plate LIKE CONCAT(#{prefix}, '-%')")
    long maxPlateNumberOf(@Param("prefix") String prefix);

    @Select("SELECT vault_path FROM post WHERE status = 'published'")
    List<String> publishedVaultPaths();

    @Update("UPDATE post SET status = 'unpublished' "
            + "WHERE vault_path = #{vaultPath} AND status = 'published'")
    int unpublish(@Param("vaultPath") String vaultPath);

    @Update("UPDATE post SET "
            + "slug = #{slug}, title = #{title}, description = #{description}, "
            + "section = #{section}, "
            + "body_md = #{bodyMd}, body_html = #{bodyHtml}, tags = #{tags}, "
            + "status = #{status}, created_at = #{createdAt}, updated_at = #{updatedAt}, "
            + "published_at = #{publishedAt}, content_hash = #{contentHash}, "
            + "git_commit_sha = #{gitCommitSha}, word_count = #{wordCount}, "
            + "read_minutes = #{readMinutes} "
            + "WHERE id = #{id}")
    int overwriteById(PostEntity post);
}
