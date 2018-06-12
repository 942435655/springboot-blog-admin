package com.blog.service.impl;

import com.blog.condition.ArticleCondition;
import com.blog.domain.Article;
import com.blog.domain.Category;
import com.blog.domain.Tag;
import com.blog.dto.ArchiveDTO;
import com.blog.dto.ArticleDTO;
import com.blog.dto.CategoryDTO;
import com.blog.enums.ArticleStatusEnum;
import com.blog.responsitory.ArticleRepository;
import com.blog.responsitory.CategoryRepository;
import com.blog.responsitory.TagRepository;
import com.blog.service.ArticleService;
import com.blog.util.BeanUtil;
import org.hibernate.SQLQuery;
import org.hibernate.transform.Transformers;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.persistence.Query;
import javax.persistence.criteria.Predicate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: yukong
 * @date: 2018/6/7 10:30
 * @description:
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    @Autowired
    private ArticleRepository articleRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TagRepository tagRepository;

    private EntityManagerFactory emf;

    @PersistenceUnit
    public void setEntityManagerFactory(EntityManagerFactory emf) {
        this.emf = emf;
    }

    @Override
    public Page<Article> listByCondition(ArticleCondition articleCondition) {
        Sort.Order modifyTimeOrder = new Sort.Order(Sort.Direction.DESC, "modifyTime");
        Sort sort = new Sort(modifyTimeOrder);
        Specification<Article> specification = ((root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<Predicate>();
            if (!StringUtils.isEmpty(articleCondition.getArticleTitle())) {
                Predicate _name = criteriaBuilder.like(root.get("articleTitle"), "%" + articleCondition.getArticleTitle() + "%");
                predicates.add(_name);
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[]{}));
        });

        Pageable pageable = new PageRequest(articleCondition.getCurrPage(), articleCondition.getPageSize(), sort);
        return articleRepository.findAll(specification, pageable);
    }

    @Override
    public void save(ArticleDTO articleDTO) {
        if (articleDTO.getId() != null && ArticleStatusEnum.PUBLISH.getCode().equals(articleDTO.getArticleStatus())) {

        } else {
            articleDTO.setPublishTime(new Date());
        }
        Article article = new Article();
        BeanUtils.copyProperties(articleDTO, article);
        Set<Tag> tags = new HashSet<>();
        articleDTO.getTagIds().forEach(e -> {
            tags.add(tagRepository.findOne(e));
        });
        article.setCategory(categoryRepository.findOne(articleDTO.getCategoryId()));
        article.setTags(tags);
        articleRepository.save(article);
    }

    @Override
    public Article getById(Long id) {
        return articleRepository.findOne(id);
    }

    @Override
    public void deleteById(Long id) {
        articleRepository.delete(id);
    }

    @Override
    public List<ArchiveDTO> archive() {
        EntityManager manager =  emf.createEntityManager();
        Query query = manager.createNativeQuery(
                "SELECT " +
                        " DATE_FORMAT( publish_time, '%m-%Y' ) date_str," +
                        " count( * ) count " +
                        " FROM" +
                        " tb_article " +
                        " GROUP BY " +
                        " DATE_FORMAT( publish_time, '%m-%Y' ) " +
                        " order by publish_time desc");
        query.unwrap(SQLQuery.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
        List<Map<String, Object>> rows = query.getResultList();
        List<ArchiveDTO> dtos = BeanUtil.transMap2Bean(rows, ArchiveDTO.class);
        manager.close();
        return dtos;
    }

    @Override
    public LinkedHashMap<Integer, List<Article>> findAllGroupByYear() {
        Sort.Order order = new Sort.Order(Sort.Direction.DESC, "publishTime");
        Sort sort = new Sort(order);
        List<Article> articles = articleRepository.findAll(sort);
        LinkedHashMap<Integer, List<Article>> group = new LinkedHashMap<>();
        articles.stream()
                .collect(Collectors.groupingBy(Article::groupByYear))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<Integer, List<Article>>comparingByKey().reversed())
                .forEachOrdered(e -> group.put(e.getKey(), e.getValue()));
        return group;
    }

    @Override
    public  LinkedHashMap<Integer,List<Article>> findByYearAndMonth(Integer year, Integer month) {
        EntityManager manager =  emf.createEntityManager();
        Query query =manager.createNativeQuery("SELECT " +
                " *  " +
                "FROM " +
                " tb_article  " +
                "WHERE " +
                " DATE_FORMAT( publish_time, '%Y' ) = ?  " +
                " AND DATE_FORMAT( publish_time, '%m' ) = ?")
                .setParameter(1,year)
                .setParameter(2,month);

        query.unwrap(SQLQuery.class).setResultTransformer(Transformers.ALIAS_TO_ENTITY_MAP);
        List<Map<String, Object>> rows = query.getResultList();
        List<Article> articles = BeanUtil.transMap2Bean(rows, Article.class);
        LinkedHashMap<Integer, List<Article>> group = new LinkedHashMap<>();
        group.put(year,articles);
        manager.close();
        return group;
    }

    @Override
    public Map<String, List<Article>> findArticleGroupByCategory() {
        Sort.Order order = new Sort.Order(Sort.Direction.DESC, "publishTime");
        Sort sort = new Sort(order);
        List<Article> articles = articleRepository.findAll(sort);
        Map<String, List<Article>> group = articles.stream()
                .collect(Collectors.groupingBy(Article::groupByCategory));
        return group;
    }

    @Override
    public Map<String, List<Article>> findArticleGroupByCategory(String categoryName) {
        Category category = categoryRepository.findCategoryByCategoryName(categoryName);
        List<Article> articles = articleRepository.findByCategoryOrderByPublishTime(category);
        Map<String, List<Article>> group = articles.stream()
                .collect(Collectors.groupingBy(Article::groupByCategory));
        return group;
    }

    @Override
    public Map<String, Set<Article>> findArticleGroupByTag() {
        List<Tag> tags = tagRepository.findAll();
        Map<String, Set<Article>> group = new HashMap<>();
        tags.forEach(e ->{
            group.put(e.getTagName(),e.getArticles());
        });
        return group;
    }

    @Override
    public Map<String, Set<Article>> findArticleGroupByTag(String tagName) {
        List<Tag> tags = tagRepository.findTagsByTagNameEquals(tagName);
        Map<String, Set<Article>> group = new HashMap<>();
        tags.forEach(e ->{
            group.put(e.getTagName(),e.getArticles());
        });
        return group;
    }
}
