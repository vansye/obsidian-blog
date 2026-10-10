package luv.vansye.sync.store;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostAssetMapper {

    @Select("SELECT post_id, asset_path, content_hash FROM post_asset WHERE post_id = #{postId}")
    List<PostAssetEntity> findByPostId(@Param("postId") Long postId);

    @Select("<script>"
            + "SELECT DISTINCT p.vault_path FROM post_asset pa "
            + "JOIN post p ON p.id = pa.post_id "
            + "WHERE pa.asset_path IN "
            + "<foreach item='path' collection='paths' open='(' separator=',' close=')'>#{path}</foreach>"
            + "</script>")
    List<String> findPostPathsByAssetPaths(@Param("paths") List<String> paths);

    @Delete("DELETE FROM post_asset WHERE post_id = #{postId}")
    int deleteByPostId(@Param("postId") Long postId);

    @Insert("INSERT INTO post_asset (post_id, asset_path, content_hash) "
            + "VALUES (#{postId}, #{assetPath}, #{contentHash})")
    int insert(@Param("postId") Long postId,
               @Param("assetPath") String assetPath,
               @Param("contentHash") String contentHash);
}
