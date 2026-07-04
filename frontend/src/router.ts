import { createRouter, createWebHistory } from 'vue-router'
import ClusterOverviewPage from './views/ClusterOverviewPage.vue'
import GuidePage from './views/GuidePage.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'overview',
      component: ClusterOverviewPage
    },
    {
      path: '/guide',
      name: 'guide',
      component: GuidePage
    }
  ]
})
