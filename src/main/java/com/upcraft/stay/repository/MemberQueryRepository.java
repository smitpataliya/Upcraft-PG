package com.upcraft.stay.repository;

import com.upcraft.stay.domain.entity.MemberQuery;
import com.upcraft.stay.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberQueryRepository extends JpaRepository<MemberQuery, UUID> {
    List<MemberQuery> findByMemberOrderByCreatedAtDesc(User member);
    List<MemberQuery> findAllByOrderByCreatedAtDesc();
}
