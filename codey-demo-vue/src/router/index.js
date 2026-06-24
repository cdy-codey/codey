import { createRouter, createWebHistory } from 'vue-router'
import ExampleHomePage from '../pages/ExampleHomePage.vue'
import ExampleDetailPage from '../pages/ExampleDetailPage.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: ExampleHomePage,
  },
  {
    path: '/examples/:exampleId',
    name: 'example-detail',
    component: ExampleDetailPage,
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})
