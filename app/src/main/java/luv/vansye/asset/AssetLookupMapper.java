package luv.vansye.asset;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetLookupMapper {

    @Select("SELECT a.content_hash, a.blob_id, a.ext, a.bytes FROM asset a "
            + "WHERE a.content_hash LIKE CONCAT(#{prefix}, '%') AND a.ext = #{ext} AND EXISTS ("
            + "SELECT 1 FROM post_asset pa JOIN post p ON p.id = pa.post_id "
            + "WHERE pa.content_hash = a.content_hash AND p.status = 'published' "
            + "AND LOWER(SUBSTRING_INDEX(pa.asset_path, '.', -1)) = SUBSTRING(a.ext, 2)) LIMIT 2")
    List<AssetRow> findByHashPrefix(@Param("prefix") String prefix, @Param("ext") String ext);
}
