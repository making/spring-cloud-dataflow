/*
 * Copyright 2016-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.server.config.features;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.dataflow.audit.service.AuditRecordService;
import org.springframework.cloud.dataflow.configuration.metadata.ApplicationConfigurationMetadataResolver;
import org.springframework.cloud.dataflow.core.TaskPlatform;
import org.springframework.cloud.dataflow.registry.service.AppRegistryService;
import org.springframework.cloud.dataflow.server.DockerValidatorProperties;
import org.springframework.cloud.dataflow.server.batch.JobService;
import org.springframework.cloud.dataflow.server.batch.SimpleJobServiceFactoryBean;
import org.springframework.cloud.dataflow.server.config.apps.CommonApplicationProperties;
import org.springframework.cloud.dataflow.server.job.LauncherRepository;
import org.springframework.cloud.dataflow.server.repository.TaskDefinitionRepository;
import org.springframework.cloud.dataflow.server.repository.TaskDeploymentRepository;
import org.springframework.cloud.dataflow.server.service.LauncherInitializationService;
import org.springframework.cloud.dataflow.server.service.TaskDeleteService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionCreationService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.TaskExecutionService;
import org.springframework.cloud.dataflow.server.service.TaskJobService;
import org.springframework.cloud.dataflow.server.service.TaskSaveService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskDeleteService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionInfoService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionRepositoryService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskExecutionService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskJobService;
import org.springframework.cloud.dataflow.server.service.impl.DefaultTaskSaveService;
import org.springframework.cloud.dataflow.server.service.impl.TaskAppDeploymentRequestCreator;
import org.springframework.cloud.dataflow.server.service.impl.TaskConfigurationProperties;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.task.repository.TaskExplorer;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Thomas Risberg
 * @author Janne Valkealahti
 * @author Glenn Renfro
 * @author Michael Minella
 * @author Ilayaperumal Gopinathan
 * @author Gunnar Hillert
 * @author Christian Tzolov
 * @author David Turanski
 */
@Configuration
@ConditionalOnTasksEnabled
@EnableConfigurationProperties({ TaskConfigurationProperties.class, CommonApplicationProperties.class,
		DockerValidatorProperties.class, LocalPlatformProperties.class
})
@EnableMapRepositories(basePackages = "org.springframework.cloud.dataflow.server.job")
@EnableTransactionManagement
public class TaskConfiguration {

	@Autowired
	DataSourceProperties dataSourceProperties;

	@Value("${spring.cloud.dataflow.server.uri:}")
	private String dataflowServerUri;

	@Bean
	public LauncherInitializationService launcherInitializationService(
			LauncherRepository launcherRepository,
			List<TaskPlatform> platforms) {
		return new LauncherInitializationService(launcherRepository, platforms);
	}

	/**
	 * The default profile is active is no other profiles are active. This is configured so
	 * that several tests will pass without having to explicitly enable the local profile.
	 * @param localPlatformProperties
	 * @return
	 */
	@Profile({ "local", "default" })
	@Bean
	public TaskPlatform localTaskPlatform(LocalPlatformProperties localPlatformProperties,
			@Nullable Scheduler localScheduler) {
		TaskPlatform taskPlatform = new LocalTaskPlatformFactory(localPlatformProperties, localScheduler)
				.createTaskPlatform();
		taskPlatform.setPrimary(true);
		return taskPlatform;
	}

	@Bean
	public TaskExecutionInfoService taskDefinitionRetriever(AppRegistryService registry,
			TaskExplorer taskExplorer, TaskDefinitionRepository taskDefinitionRepository,
			TaskConfigurationProperties taskConfigurationProperties,
			LauncherRepository launcherRepository, List<TaskPlatform> taskPlatforms) {
		return new DefaultTaskExecutionInfoService(dataSourceProperties, registry,
				taskExplorer, taskDefinitionRepository, taskConfigurationProperties, launcherRepository, taskPlatforms);
	}

	@Bean
	public TaskDeleteService deleteTaskService(TaskExplorer taskExplorer, LauncherRepository launcherRepository,
			TaskDefinitionRepository taskDefinitionRepository, TaskDeploymentRepository taskDeploymentRepository,
			AuditRecordService auditRecordService) {
		return new DefaultTaskDeleteService(taskExplorer, launcherRepository, taskDefinitionRepository,
				taskDeploymentRepository,
				auditRecordService);
	}

	@Bean
	public TaskSaveService saveTaskService(TaskDefinitionRepository taskDefinitionRepository,
			AuditRecordService auditRecordService, AppRegistryService registry) {
		return new DefaultTaskSaveService(taskDefinitionRepository, auditRecordService, registry);
	}

	@Bean
	public TaskExecutionCreationService taskExecutionRepositoryService(TaskRepository taskRepository) {
		return new DefaultTaskExecutionRepositoryService(taskRepository);
	}

	@Bean
	public TaskAppDeploymentRequestCreator taskAppDeploymentRequestCreator(
			CommonApplicationProperties commonApplicationProperties,
			ApplicationConfigurationMetadataResolver metadataResolver) {
		return new TaskAppDeploymentRequestCreator(commonApplicationProperties,
				metadataResolver, dataflowServerUri);
	}

	@Bean
	public TaskExecutionService taskService(LauncherRepository launcherRepository,
			AuditRecordService auditRecordService,
			TaskRepository taskRepository,
			TaskExecutionInfoService taskExecutionInfoService,
			TaskDeploymentRepository taskDeploymentRepository,
			TaskExecutionCreationService taskExecutionRepositoryService,
			TaskAppDeploymentRequestCreator taskAppDeploymentRequestCreator) {
		return new DefaultTaskExecutionService(
				launcherRepository, auditRecordService, taskRepository,
				taskExecutionInfoService, taskDeploymentRepository, taskExecutionRepositoryService,
				taskAppDeploymentRequestCreator);
	}

	@Bean
	public TaskJobService taskJobExecutionRepository(JobService service, TaskExplorer taskExplorer,
			TaskDefinitionRepository taskDefinitionRepository, TaskExecutionService taskExecutionService) {
		return new DefaultTaskJobService(service, taskExplorer, taskDefinitionRepository, taskExecutionService);
	}

	@Bean
	public SimpleJobServiceFactoryBean simpleJobServiceFactoryBean(DataSource dataSource,
			JobRepositoryFactoryBean repositoryFactoryBean, JobExplorer jobExplorer,
			PlatformTransactionManager dataSourceTransactionManager) throws Exception {
		SimpleJobServiceFactoryBean factoryBean = new SimpleJobServiceFactoryBean();
		factoryBean.setDataSource(dataSource);
		factoryBean.setJobRepository(repositoryFactoryBean.getObject());
		factoryBean.setJobLocator(new MapJobRegistry());
		factoryBean.setJobLauncher(new SimpleJobLauncher());
		factoryBean.setDataSource(dataSource);
		factoryBean.setJobExplorer(jobExplorer);
		factoryBean.setTransactionManager(dataSourceTransactionManager);
		return factoryBean;
	}

	@Bean
	public JobExplorerFactoryBean jobExplorerFactoryBean(DataSource dataSource) {
		JobExplorerFactoryBean jobExplorerFactoryBean = new JobExplorerFactoryBean();
		jobExplorerFactoryBean.setDataSource(dataSource);
		return jobExplorerFactoryBean;
	}

	@Bean
	public JobRepositoryFactoryBean jobRepositoryFactoryBean(DataSource dataSource,
			PlatformTransactionManager platformTransactionManager) {
		JobRepositoryFactoryBean repositoryFactoryBean = new JobRepositoryFactoryBean();
		repositoryFactoryBean.setDataSource(dataSource);
		repositoryFactoryBean.setTransactionManager(platformTransactionManager);
		return repositoryFactoryBean;
	}
}
