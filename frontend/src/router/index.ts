import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/templates'
    },
    {
      path: '/templates',
      name: 'TemplateList',
      component: () => import('@/views/template/TemplateList.vue'),
      meta: { title: '模板列表', menu: 'template' }
    },
    {
      path: '/templates/:id',
      name: 'TemplateDetail',
      component: () => import('@/views/template/TemplateDetail.vue'),
      meta: { title: '模板详情', menu: 'template' }
    },
    {
      path: '/engine-config',
      name: 'EngineConfig',
      component: () => import('@/views/template/EngineConfig.vue'),
      meta: { title: '引擎配置', menu: 'template' }
    },
    {
      path: '/file-upload',
      name: 'FileUpload',
      component: () => import('@/views/file/FileUpload.vue'),
      meta: { title: '文件上传', menu: 'file' }
    },
    {
      path: '/parse-preview',
      name: 'ParsePreview',
      component: () => import('@/views/file/ParsePreview.vue'),
      meta: { title: '解析预览', menu: 'file' }
    }
  ]
})

export default router
