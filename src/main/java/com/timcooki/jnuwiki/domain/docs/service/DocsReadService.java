package com.timcooki.jnuwiki.domain.docs.service;

import com.timcooki.jnuwiki.domain.docs.DTO.request.FindAllReqDTO;
import com.timcooki.jnuwiki.domain.docs.DTO.response.*;
import com.timcooki.jnuwiki.domain.docs.entity.Docs;
import com.timcooki.jnuwiki.domain.docs.mapper.DocsMapper;
import com.timcooki.jnuwiki.domain.docs.repository.DocsRepository;
import com.timcooki.jnuwiki.domain.member.entity.Member;
import com.timcooki.jnuwiki.domain.member.repository.MemberRepository;
import com.timcooki.jnuwiki.domain.scrap.entity.Scrap;
import com.timcooki.jnuwiki.domain.scrap.entity.ScrapId;
import com.timcooki.jnuwiki.domain.scrap.repository.ScrapRepository;
import com.timcooki.jnuwiki.util.errors.exception.Exception404;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocsReadService {
    private final DocsRepository docsRepository;
    private final MemberRepository memberRepository;
    private final ScrapRepository scrapRepository;

    // SecurityContextHolder로 로그인한 유저의 정보를 가져온다.
    private String getEmail(){
        Authentication loggedInUser = SecurityContextHolder.getContext().getAuthentication();
        String email = loggedInUser.getName();
        return email;
    }

    public ListReadResDTO getDocsList(Pageable pageable, FindAllReqDTO findAllReqDTO) {
        Page<Docs> docsList = docsRepository.mfindAll(findAllReqDTO.rightUp(),findAllReqDTO.leftDown(), pageable);
        Optional<Member> member = memberRepository.findByEmail(getEmail());
        List<Scrap> scrapList = member.isPresent() ? scrapRepository.findAllByMemberId(member.get().getMemberId()) : new ArrayList<>();

        int totalPages = docsList.getTotalPages();


        List<OneOfListReadResDTO> result = docsList.getContent().stream()
                .map(d -> OneOfListReadResDTO.builder()
                        .docsId(d.getDocsId())
                                .docsName(d.getDocsName())
                                .docsCategory(d.getDocsCategory().getCategory())
                                .scrap(!scrapList.isEmpty() && scrapList.stream()
                                        .anyMatch(s -> Objects.equals(s.getDocsId(), d.getDocsId()))
                                )
                                .build()
                        )
                .collect(Collectors.toList());



        return ListReadResDTO.builder()
                .docsList(result)
                .totalPages(totalPages)
                .build();
    }



    public ReadResDTO getOneDocs(String email, Long docsId) {
        boolean scrap = false;
        Docs docs = docsRepository.findById(docsId).orElseThrow(
                () -> new Exception404("존재하지 않는 문서입니다.")
        );

        if (email != null) {
            Member member = memberRepository.findByEmail(email).get();
            if (scrapRepository.findById(new ScrapId(member.getMemberId(), docs.getDocsId())).isPresent()) scrap = true;
        }

        return new ReadResDTO(
                docs.getDocsId(),
                docs.getDocsName(),
                docs.getDocsCategory().getCategory(),
                docs.getDocsLocation(),
                docs.getDocsContent(),
                docs.getCreatedBy().getNickName(),
                docs.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")),
                scrap
        );
    }

    public List<SearchReadResDTO> searchLike(String search) {
        List<Docs> docsList = docsRepository.searchLike(search);
        if (docsList != null && !docsList.isEmpty()) {
            DocsMapper mapper = Mappers.getMapper(DocsMapper.class);

            List<SearchReadResDTO> res = docsList.stream().map((docs -> mapper.entityToDTO(docs, docs.getCreatedBy().getNickName(), docs.getDocsCategory().getCategory()))).toList();

            return res;
        } else {
            throw new Exception404("요청결과가 존재하지 않습니다.");
        }

    }
}